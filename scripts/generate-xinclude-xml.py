#!/usr/bin/env python3
"""
Generate merged XML files from master files using XML entity expansion.
This script is called by pre-commit when master or part files change.
"""

import subprocess
import sys
from pathlib import Path

# Configuration
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
CONFIG_DIR = PROJECT_ROOT / "dspace" / "config"

FILES_TO_GENERATE = [
    {
        "master": CONFIG_DIR / "submission-forms.master.xml",
        "output": CONFIG_DIR / "submission-forms.xml",
    },
    {
        "master": CONFIG_DIR / "item-submission.master.xml",
        "output": CONFIG_DIR / "item-submission.xml",
    },
]


def generate_xml(master: Path, output: Path) -> bool:
    """
    Generate merged XML from master file using xmllint entity expansion.
    Returns True if generation succeeded.
    """
    if not master.exists():
        print(f"Warning: Master file not found: {master}", file=sys.stderr)
        return False

    try:
        result = subprocess.run(
            ["xmllint", "--noent", "--output", str(output), str(master)],
            capture_output=True,
            text=True,
            check=True,
            cwd=CONFIG_DIR,  # Important: run from config dir for relative paths
        )
        print(f"Generated: {output.name}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"Error generating {output.name}:", file=sys.stderr)
        print(e.stderr, file=sys.stderr)
        return False
    except FileNotFoundError:
        print("Error: xmllint not found. Please install libxml2.", file=sys.stderr)
        print("  macOS: brew install libxml2 (or use system xmllint)", file=sys.stderr)
        print("  Linux: apt install libxml2-utils", file=sys.stderr)
        return False


def main():
    """Generate all XML files and stage them for commit."""
    success = True
    changed_files = []

    for config in FILES_TO_GENERATE:
        if generate_xml(config["master"], config["output"]):
            changed_files.append(config["output"])
        else:
            success = False

    # Stage the generated files for the current commit
    if changed_files:
        files_to_stage = [str(f) for f in changed_files]
        subprocess.run(["git", "add"] + files_to_stage, check=True)

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
