#!/usr/bin/env python3
"""
Build corpus.jsonl from PubMed and ClinicalTrials.gov.

Standard library only. Run this first, then tag_evidence.py.

    python3 fetch_corpus.py --out corpus.jsonl --per-compound 120

NCBI asks for no more than 3 requests/second without an API key. Set one with
--ncbi-key to raise that to 10/s and be a better citizen.
"""

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
CTGOV = "https://clinicaltrials.gov/api/v2/studies"
USER_AGENT = "PeptideAdvisor/1.0 (personal research corpus builder)"

# Edit this list. Aliases matter — PubMed indexes these inconsistently.
COMPOUNDS = {
    "BPC-157": ["BPC 157", "body protection compound 157", "pentadecapeptide BPC 157"],
    "TB-500": ["thymosin beta 4", "TB4", "Tβ4"],
    "Ipamorelin": [],
    "CJC-1295": ["CJC 1295", "modified GRF 1-29"],
    "Tesamorelin": ["Egrifta"],
    "Sermorelin": [],
    "Semaglutide": ["Ozempic", "Wegovy"],
    "Tirzepatide": ["Mounjaro", "Zepbound"],
    "Retatrutide": [],
    "GHK-Cu": ["copper tripeptide", "glycyl-histidyl-lysine"],
    "Thymosin alpha-1": ["thymalfasin", "thymosin alpha 1"],
    "PT-141": ["bremelanotide"],
    "Melanotan II": ["melanotan-2", "afamelanotide"],
    "Selank": [],
    "Semax": [],
    "Epitalon": ["epithalon", "epithalamin"],
    "MOTS-c": ["MOTS c"],
    "5-Amino-1MQ": ["5-amino-1-methylquinolinium"],
    "AOD-9604": ["AOD 9604"],
    "Hexarelin": [],
    "GHRP-2": ["pralmorelin"],
    "GHRP-6": [],
    "KPV": ["lysine-proline-valine"],
    "LL-37": ["cathelicidin LL-37"],
}


def get(url, retries=3, pause=0.4):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=45) as resp:
                return resp.read()
        except Exception as exc:  # noqa: BLE001
            if attempt == retries - 1:
                print(f"  ! failed: {exc}", file=sys.stderr)
                return None
            time.sleep(pause * (attempt + 2))
    return None


# ---------------------------------------------------------------- PubMed

def pubmed_search(term, retmax, ncbi_key=None):
    params = {
        "db": "pubmed",
        "term": term,
        "retmax": str(retmax),
        "retmode": "json",
        "sort": "relevance",
    }
    if ncbi_key:
        params["api_key"] = ncbi_key
    raw = get(f"{EUTILS}/esearch.fcgi?{urllib.parse.urlencode(params)}")
    if not raw:
        return []
    try:
        return json.loads(raw)["esearchresult"].get("idlist", [])
    except (KeyError, json.JSONDecodeError):
        return []


def pubmed_fetch(pmids, ncbi_key=None):
    if not pmids:
        return []
    params = {"db": "pubmed", "id": ",".join(pmids), "retmode": "xml"}
    if ncbi_key:
        params["api_key"] = ncbi_key
    raw = get(f"{EUTILS}/efetch.fcgi?{urllib.parse.urlencode(params)}")
    if not raw:
        return []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        print(f"  ! XML parse error: {exc}", file=sys.stderr)
        return []

    out = []
    for article in root.findall(".//PubmedArticle"):
        pmid_el = article.find(".//PMID")
        if pmid_el is None:
            continue
        pmid = pmid_el.text

        title = "".join(article.find(".//ArticleTitle").itertext()) \
            if article.find(".//ArticleTitle") is not None else ""

        # Abstracts come in labelled sections; flatten them.
        chunks = []
        for node in article.findall(".//Abstract/AbstractText"):
            label = node.get("Label")
            text = "".join(node.itertext()).strip()
            chunks.append(f"{label}: {text}" if label else text)
        abstract = " ".join(c for c in chunks if c)

        journal_el = article.find(".//Journal/ISOAbbreviation")
        if journal_el is None:
            journal_el = article.find(".//Journal/Title")
        journal = journal_el.text if journal_el is not None else None

        year = None
        for path in (".//JournalIssue/PubDate/Year", ".//JournalIssue/PubDate/MedlineDate"):
            el = article.find(path)
            if el is not None and el.text:
                match = re.search(r"(19|20)\d{2}", el.text)
                if match:
                    year = int(match.group(0))
                    break

        pub_types = [pt.text for pt in article.findall(".//PublicationType") if pt.text]
        mesh = [d.text for d in article.findall(".//MeshHeading/DescriptorName") if d.text]

        out.append({
            "id": f"PMID:{pmid}",
            "source": "pubmed",
            "title": title.strip(),
            "abstract": abstract.strip(),
            "year": year,
            "journal": journal,
            "url": f"https://pubmed.ncbi.nlm.nih.gov/{pmid}/",
            "compounds": [],
            "tier": "unknown",
            "tierRationale": None,
            "species": None,
            "sampleSize": None,
            "_publicationTypes": pub_types,
            "_mesh": mesh,
        })
    return out


# ------------------------------------------------------- ClinicalTrials.gov

def ctgov_search(term, page_size=40):
    params = {
        "query.term": term,
        "pageSize": str(page_size),
        "fields": "NCTId,BriefTitle,BriefSummary,OverallStatus,StudyType,"
                  "DesignAllocation,EnrollmentCount,StartDate,Phase",
    }
    raw = get(f"{CTGOV}?{urllib.parse.urlencode(params)}")
    if not raw:
        return []
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return []

    out = []
    for study in payload.get("studies", []):
        protocol = study.get("protocolSection", {})
        ident = protocol.get("identificationModule", {})
        design = protocol.get("designModule", {})
        desc = protocol.get("descriptionModule", {})
        status = protocol.get("statusModule", {})

        nct = ident.get("nctId")
        if not nct:
            continue

        allocation = design.get("designInfo", {}).get("allocation", "")
        enrollment = design.get("enrollmentInfo", {}).get("count")
        start = status.get("startDateStruct", {}).get("date", "")
        year = int(start[:4]) if start[:4].isdigit() else None

        # Registry records are self-describing enough to pre-tag.
        tier = "human_rct" if allocation == "RANDOMIZED" else "human_controlled"
        if design.get("studyType") == "OBSERVATIONAL":
            tier = "human_observational"

        out.append({
            "id": nct,
            "source": "clinicaltrials",
            "title": ident.get("briefTitle", ""),
            "abstract": desc.get("briefSummary", ""),
            "year": year,
            "journal": f"ClinicalTrials.gov · {status.get('overallStatus', 'unknown status')}",
            "url": f"https://clinicaltrials.gov/study/{nct}",
            "compounds": [],
            "tier": tier,
            "tierRationale": f"Registry record: {design.get('studyType', 'interventional')}"
                             f"{', ' + allocation.lower() if allocation else ''}",
            "species": "human",
            "sampleSize": enrollment,
        })
    return out


# ---------------------------------------------------------------- Assembly

def build_query(name, aliases):
    terms = [name] + aliases
    joined = " OR ".join(f'"{t}"[All Fields]' for t in terms)
    return f"({joined})"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="corpus.jsonl")
    parser.add_argument("--per-compound", type=int, default=120,
                        help="max PubMed records per compound")
    parser.add_argument("--trials", type=int, default=25,
                        help="max registry records per compound")
    parser.add_argument("--ncbi-key", default=None)
    parser.add_argument("--skip-trials", action="store_true")
    args = parser.parse_args()

    pause = 0.12 if args.ncbi_key else 0.36
    records = {}

    for name, aliases in COMPOUNDS.items():
        query = build_query(name, aliases)
        print(f"→ {name}")

        pmids = pubmed_search(query, args.per_compound, args.ncbi_key)
        print(f"   pubmed: {len(pmids)} hits")
        time.sleep(pause)

        for i in range(0, len(pmids), 100):
            batch = pubmed_fetch(pmids[i:i + 100], args.ncbi_key)
            for record in batch:
                existing = records.get(record["id"])
                if existing:
                    if name not in existing["compounds"]:
                        existing["compounds"].append(name)
                else:
                    record["compounds"] = [name]
                    records[record["id"]] = record
            time.sleep(pause)

        if not args.skip_trials:
            for trial in ctgov_search(name, args.trials):
                existing = records.get(trial["id"])
                if existing:
                    if name not in existing["compounds"]:
                        existing["compounds"].append(name)
                else:
                    trial["compounds"] = [name]
                    records[trial["id"]] = trial
            time.sleep(0.25)

    # Abstracts are what the app retrieves against; a bare title is noise.
    kept = [r for r in records.values() if len(r.get("abstract") or "") > 120]
    dropped = len(records) - len(kept)

    with open(args.out, "w", encoding="utf-8") as fh:
        for record in kept:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")

    print(f"\nwrote {len(kept)} records to {args.out} ({dropped} dropped for thin abstracts)")
    untagged = sum(1 for r in kept if r["tier"] == "unknown")
    print(f"{untagged} need tagging — run: python3 tag_evidence.py --in {args.out}")


if __name__ == "__main__":
    main()
