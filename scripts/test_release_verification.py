"""Exercise the actual inline health verifier offline; no production requests."""

from contextlib import redirect_stdout
import io
from pathlib import Path
import re
import textwrap
import unittest
from unittest.mock import Mock, patch
import urllib.error


workflow = Path(__file__).resolve().parents[1] / ".github/workflows/verify-production-release.yml"
match = re.search(r"python3 - <<'PY'\n(.*?)\n          PY", workflow.read_text(), re.S)
program = compile(textwrap.dedent(match.group(1)), "inline-release-verification", "exec")


class Response(io.BytesIO):
    status = 200


def healthy(request, timeout):
    assert timeout == 10
    return Response(b'<title>Photocalia</title>' if request.full_url.endswith(".com/") else b'{"status":"UP"}')


def http_error(code):
    return urllib.error.HTTPError("https://synthetic.invalid/", code, "synthetic", {}, io.BytesIO())


class ReleaseVerificationTests(unittest.TestCase):
    def execute(self, effect):
        opener = Mock()
        opener.open.side_effect = effect
        with patch("urllib.request.build_opener", return_value=opener), patch("time.sleep") as sleep, redirect_stdout(io.StringIO()):
            self.last_opener, self.last_sleep = opener, sleep
            exec(program, {})
        return opener, sleep

    def test_requires_both_api_domains_and_frontend(self):
        opener, sleep = self.execute(healthy)
        self.assertEqual([call.args[0].full_url for call in opener.open.call_args_list], [
            "https://api.photocalia.com/health", "https://api.3dime.com/health", "https://www.photocalia.com/",
        ])
        sleep.assert_not_called()

    def test_transient_startup_recovers_then_checks_every_endpoint(self):
        calls = 0
        def effect(request, timeout):
            nonlocal calls
            calls += 1
            if calls == 1:
                raise http_error(503)
            return healthy(request, timeout)
        opener, sleep = self.execute(effect)
        self.assertEqual(opener.open.call_count, 4)
        sleep.assert_called_once_with(10)

    def test_persistent_failure_has_exactly_six_attempts(self):
        opener = Mock()
        opener.open.side_effect = lambda *_args, **_kwargs: (_ for _ in ()).throw(http_error(503))
        with patch("urllib.request.build_opener", return_value=opener), patch("time.sleep") as sleep, redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
            exec(program, {})
        self.assertEqual(opener.open.call_count, 6)
        self.assertEqual(sleep.call_count, 5)

    def test_auth_and_redirect_errors_are_not_retried(self):
        for code in [301, 302, 401, 403, 404]:
            with self.subTest(code=code), patch("time.sleep") as sleep:
                with self.assertRaises(SystemExit):
                    self.execute(lambda *_args, **_kwargs: (_ for _ in ()).throw(http_error(code)))
                self.assertEqual(self.last_opener.open.call_count, 1)
                self.last_sleep.assert_not_called()

    def test_down_malformed_or_unrecognized_health_never_passes(self):
        for data in [b'{"status":"DOWN"}', b'[]', b'not-json', b'{}']:
            with self.subTest(data=data), self.assertRaises(SystemExit):
                self.execute(lambda *_args, **_kwargs: Response(data))

    def test_wrong_frontend_identity_never_passes(self):
        def effect(request, timeout):
            return Response(b'<title>Other</title>') if request.full_url.endswith(".com/") else healthy(request, timeout)
        with self.assertRaises(SystemExit):
            self.execute(effect)

    def test_oversized_response_never_passes(self):
        with self.assertRaises(SystemExit):
            self.execute(lambda *_args, **_kwargs: Response(b'x' * 1_048_577))

    def test_network_timeout_can_recover(self):
        calls = 0
        def effect(request, timeout):
            nonlocal calls
            calls += 1
            if calls == 1:
                raise TimeoutError()
            return healthy(request, timeout)
        opener, sleep = self.execute(effect)
        self.assertEqual(opener.open.call_count, 4)
        sleep.assert_called_once_with(10)


if __name__ == "__main__":
    unittest.main()
