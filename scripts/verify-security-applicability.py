"""Fail closed if components excluded by the narrow CVE triage enter the package."""

from pathlib import Path
import sys
import zipfile


def verify(build_dir: Path) -> None:
    runner = build_dir / "3dime-api-runner.jar"
    if not runner.is_file() or runner.stat().st_size == 0:
        raise ValueError("Missing packaged runner")
    jars = [runner, *sorted((build_dir / "quarkus").rglob("*.jar"))]
    if len(jars) > 500 or sum(p.stat().st_size for p in jars) > 1024**3:
        raise ValueError("Package inspection bound exceeded")
    count = 0
    runtime_present = False
    forbidden = ("io/vertx/db2client/", "org/eclipse/jetty/http/", "org/eclipse/jetty/server/")
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.infolist():
                count += 1
                if count > 200_000:
                    raise ValueError("Package entry bound exceeded")
                name = entry.filename.replace("\\", "/")
                if name.endswith(".jar"):
                    raise ValueError("Nested archive requires renewed applicability review")
                if any(prefix in name for prefix in forbidden):
                    raise ValueError("Affected component present; remove exclusions and reassess")
                runtime_present |= name == "io/vertx/core/Vertx.class"
    if not runtime_present:
        raise ValueError("Expected Vert.x runtime missing; package layout needs review")
    print("Applicability verified: no DB2 client or Jetty HTTP/server implementation in runtime package")


if __name__ == "__main__":
    try:
        verify(Path(sys.argv[1]) if len(sys.argv) == 2 else Path("target"))
    except (OSError, ValueError, zipfile.BadZipFile):
        print("Security applicability verification failed; inspect package and triage before release", file=sys.stderr)
        sys.exit(1)
