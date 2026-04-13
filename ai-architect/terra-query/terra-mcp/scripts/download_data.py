"""
Download disaster data files to terra-mcp/data/.

Usage:
    python scripts/download_data.py [--data-dir /path/to/data]

Data sources:
- EOSDIS (Kaggle): requires Kaggle API token (~22k records, global, 1900–2021)
- NOAA Storm Events: public FTP, downloads latest CSV year files

The script downloads to DATA_DIR (default: ../data/).
Existing files are skipped unless --force is passed.
"""
import argparse
import logging
import os
import sys
import zipfile
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_DATA_DIR = Path(__file__).parent.parent / "data"

# NOAA Storm Events — latest full year CSV
NOAA_DETAIL_URL_TPL = (
    "https://www.ncei.noaa.gov/pub/data/swdi/stormevents/csvfiles/"
    "StormEvents_details-ftp_v1.0_d{year}_c{created}.csv.gz"
)

# NASA EONET v3 — all events endpoint (fallback, no CSV)
EONET_ALL_EVENTS_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=all&limit=2000"


def download_noaa(data_dir: Path, force: bool = False) -> None:
    """Download NOAA Storm Events detail CSV for the most recent available year."""
    try:
        import httpx
    except ImportError:
        logger.error("httpx not installed. Run: pip install httpx")
        sys.exit(1)

    import datetime
    year = datetime.date.today().year - 1  # last full year
    out_path = data_dir / "noaa.csv"

    if out_path.exists() and not force:
        logger.info("NOAA file already exists at %s — skipping (use --force to re-download)", out_path)
        return

    # Try to find the file listing for the year
    index_url = "https://www.ncei.noaa.gov/pub/data/swdi/stormevents/csvfiles/"
    logger.info("Fetching NOAA file index from %s", index_url)
    with httpx.Client(timeout=30) as client:
        resp = client.get(index_url)
        resp.raise_for_status()
        # Find the details file for the target year
        import re
        pattern = re.compile(
            rf"StormEvents_details-ftp_v1\.0_d{year}_c\d{{8}}\.csv\.gz"
        )
        matches = pattern.findall(resp.text)
        if not matches:
            logger.warning("No NOAA file found for year %d — trying previous year", year)
            year -= 1
            pattern = re.compile(
                rf"StormEvents_details-ftp_v1\.0_d{year}_c\d{{8}}\.csv\.gz"
            )
            matches = pattern.findall(resp.text)

        if not matches:
            logger.error("Could not find NOAA storm events file")
            return

        filename = sorted(matches)[-1]  # most recently created
        file_url = f"{index_url}{filename}"
        logger.info("Downloading %s", file_url)

        gz_path = data_dir / filename
        with client.stream("GET", file_url) as r:
            r.raise_for_status()
            with open(gz_path, "wb") as f:
                for chunk in r.iter_bytes(chunk_size=8192):
                    f.write(chunk)

    import gzip, shutil
    logger.info("Extracting %s → %s", gz_path, out_path)
    with gzip.open(gz_path, "rb") as gz_in, open(out_path, "wb") as csv_out:
        shutil.copyfileobj(gz_in, csv_out)
    gz_path.unlink(missing_ok=True)
    logger.info("NOAA downloaded: %s", out_path)


def download_eosdis_kaggle(data_dir: Path, force: bool = False) -> None:
    """
    Download EOSDIS natural disasters CSV from Kaggle.
    Requires KAGGLE_USERNAME and KAGGLE_KEY env vars (or ~/.kaggle/kaggle.json).
    Dataset: brsdincer/all-natural-disasters-19002021-eosdis
    """
    out_path = data_dir / "eosdis.csv"
    if out_path.exists() and not force:
        logger.info("EOSDIS file already exists at %s — skipping", out_path)
        return

    try:
        from kaggle.api.kaggle_api_extended import KaggleApiExtended
    except ImportError:
        logger.error("kaggle package not installed. Run: pip install kaggle")
        logger.info("Then set KAGGLE_USERNAME and KAGGLE_KEY env vars.")
        sys.exit(1)

    api = KaggleApiExtended()
    api.authenticate()
    logger.info("Downloading EOSDIS dataset from Kaggle...")
    api.dataset_download_files(
        "brsdincer/all-natural-disasters-19002021-eosdis",
        path=str(data_dir),
        unzip=True,
    )
    # Rename to expected filename
    for csv in data_dir.glob("*.csv"):
        if "disaster" in csv.name.lower() or "natural" in csv.name.lower():
            csv.rename(out_path)
            break
    logger.info("EOSDIS downloaded: %s", out_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR,
                        help="Directory to download data files into")
    parser.add_argument("--force", action="store_true",
                        help="Re-download even if files already exist")
    parser.add_argument("--source", choices=["eosdis", "noaa", "all"], default="all",
                        help="Which source to download (default: all)")
    args = parser.parse_args()

    args.data_dir.mkdir(parents=True, exist_ok=True)

    if args.source in ("noaa", "all"):
        download_noaa(args.data_dir, force=args.force)

    if args.source in ("eosdis", "all"):
        download_eosdis_kaggle(args.data_dir, force=args.force)

    logger.info("Download complete. Files in: %s", args.data_dir)


if __name__ == "__main__":
    main()
