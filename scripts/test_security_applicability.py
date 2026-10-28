"""Regression coverage for the release applicability boundary."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("applicability", Path(__file__).with_name("verify-security-applicability.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApplicabilityTests(unittest.TestCase):
    def test_no_vertx_db2_advisory_exclusion_remains(self):
        root = ET.parse(Path(__file__).resolve().parents[1] / "dependency-check-suppressions.xml").getroot()
        cves = [node.text for node in root.iter() if node.tag.endswith("}cve")]
        self.assertNotIn("CVE-2026-102469", cves)
        self.assertEqual(set(cves), {"CVE-2017-7657", "CVE-2017-7658"})
        self.assertFalse(any(node.tag.endswith("}packageUrl") for node in root.iter()))

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
