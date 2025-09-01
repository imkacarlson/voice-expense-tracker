#!/usr/bin/env python3
import argparse
import os
import shutil
import subprocess
import sys


def check(cmd):
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return True
    except Exception:
        return False


def which(name):
    return shutil.which(name) is not None


def main():
    parser = argparse.ArgumentParser(description="Build Android APK with sanity checks")
    parser.add_argument("--release", action="store_true", help="Build release instead of debug")
    parser.add_argument("--install", action="store_true", help="Install on connected device via adb")
    parser.add_argument("--module", default="app", help="Gradle module (default: app)")
    parser.add_argument("--keystore", help="Path to keystore for release signing")
    parser.add_argument("--storepass", help="Keystore password")
    parser.add_argument("--keyalias", help="Key alias")
    args = parser.parse_args()

    # Prereq checks
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    java = os.environ.get("JAVA_HOME")
    problems = []
    if not sdk:
        problems.append("ANDROID_HOME or ANDROID_SDK_ROOT not set")
    if not java:
        problems.append("JAVA_HOME not set")
    if not which("adb"):
        problems.append("adb not found in PATH")
    if problems:
        print("Prerequisite checks failed:")
        for p in problems:
            print(f" - {p}")
        print("Fix the above, then re-run this script.")
        sys.exit(2)

    # Determine gradle command
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    if not os.path.exists(gradlew):
        gradlew = "gradle"

    variant = "Release" if args.release else "Debug"
    task = f":{args.module}:assemble{variant}"

    print(f"Running {gradlew} {task}…")
    code = subprocess.call([gradlew, task])
    if code != 0:
        print("Gradle build failed", file=sys.stderr)
        sys.exit(code)

    apk = os.path.join(args.module, "build", "outputs", "apk", variant.lower(), f"{args.module}-{variant.lower()}.apk")
    apk_abs = os.path.abspath(apk)
    if not os.path.exists(apk):
        print(f"APK not found at {apk}", file=sys.stderr)
        sys.exit(1)

    print(f"Built APK: {apk_abs}")

    if args.install:
        print("Installing via adb…")
        code = subprocess.call(["adb", "install", "-r", apk_abs])
        if code != 0:
            print("adb install failed", file=sys.stderr)
            sys.exit(code)
        print("Installed successfully.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

