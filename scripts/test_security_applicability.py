"""Regression coverage for the release applicability boundary."""

import importlib.util
import re
from pathlib import Path
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("applicability", Path(__file__).with_name("verify-security-applicability.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApplicabilityTests(unittest.TestCase):
    def test_exclusion_never_matches_db2_other_versions_or_other_groups(self):
        root = ET.parse(Path(__file__).resolve().parents[1] / "dependency-check-suppressions.xml").getroot()
        pattern = next(node.text for node in root.iter() if node.tag.endswith("}packageUrl"))
        for name in ["vertx-core", "vertx-web", "vertx-web-client", "vertx-web-common", "vertx-grpc", "vertx-grpc-server"]:
            self.assertRegex(f"pkg:maven/io.vertx/{name}@4.5.34?type=jar", pattern)
        for url in ["pkg:maven/io.vertx/vertx-db2-client@4.5.34", "pkg:maven/io.vertx/vertx-db2-client@4.5.34?type=jar", "pkg:maven/io.vertx/vertx-web@4.5.35", "pkg:maven/other/vertx-web@4.5.34", "pkg:maven/io.vertx/vertx-web@4.5.340"]:
            self.assertIsNone(re.search(pattern, url))

    def package(self, root, extra=()):
        with zipfile.ZipFile(root / "3dime-api-runner.jar", "w") as archive:
            archive.writestr("io/vertx/core/Vertx.class", b"synthetic")
            for name in extra:
                archive.writestr(name, b"synthetic")

    def test_expected_runtime_and_npn_api_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.package(root, ["org/eclipse/jetty/npn/NextProtoNego.class"])
            module.verify(root)

    def test_affected_components_and_nested_archives_block(self):
        for name in ["io/vertx/db2client/DB2Pool.class", "org/eclipse/jetty/http/HttpParser.class", "org/eclipse/jetty/server/Server.class", "BOOT-INF/lib/unknown.jar"]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.package(root, [name])
                with self.assertRaises(ValueError):
                    module.verify(root)

    def test_additional_runtime_jar_is_inspected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.package(root)
            (root / "quarkus").mkdir()
            with zipfile.ZipFile(root / "quarkus" / "extra.jar", "w") as archive:
                archive.writestr("io/vertx/db2client/DB2Pool.class", b"synthetic")
            with self.assertRaises(ValueError):
                module.verify(root)

    def test_missing_empty_or_unrecognized_package_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                module.verify(root)
            with zipfile.ZipFile(root / "3dime-api-runner.jar", "w"):
                pass
            with self.assertRaises(ValueError):
                module.verify(root)


if __name__ == "__main__":
    unittest.main()
