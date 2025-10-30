#!/usr/bin/env python3
"""Build a standalone executable for the library reservation service using PyInstaller."""

from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path


def main() -> None:
    try:
        import PyInstaller.__main__  # type: ignore
    except ImportError as exc:  # pragma: no cover - PyInstaller is optional at runtime
        print(
            "PyInstaller is required to build the executable. "
            "Install it first with `pip install pyinstaller`.",
            file=sys.stderr,
        )
        raise SystemExit(1) from exc

    project_root = Path(__file__).resolve().parent
    backend_dir = project_root / "backend"
    frontend_dir = project_root / "frontend"

    if not backend_dir.exists():
        print("Could not find the backend directory.", file=sys.stderr)
        raise SystemExit(1)

    if not frontend_dir.exists():
        print("Could not find the frontend directory.", file=sys.stderr)
        raise SystemExit(1)

    build_dir = project_root / "build"
    dist_dir = project_root / "dist"

    for path in (build_dir, dist_dir):
        if path.exists():
            shutil.rmtree(path)

    add_data_args = [
        "--add-data",
        f"{frontend_dir / 'templates'}{os.pathsep}frontend/templates",
        "--add-data",
        f"{frontend_dir / 'static'}{os.pathsep}frontend/static",
    ]

    PyInstaller.__main__.run(
        [
            str(backend_dir / "app.py"),
            "--name",
            "fuck_njfu_lib",
            "--onefile",
            "--clean",
            "--noconfirm",
            "--paths",
            str(backend_dir),
            "--paths",
            str(project_root),
            *add_data_args,
        ]
    )

    executable = dist_dir / "fuck_njfu_lib"
    if sys.platform == "win32":
        executable = executable.with_suffix(".exe")

    print(f"\nExecutable generated at: {executable}")


if __name__ == "__main__":
    main()
