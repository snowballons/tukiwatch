import json
import streamlink
from streamlink import Streamlink
from streamlink.exceptions import NoPluginError, PluginError, StreamlinkError
from streamlink.stream.hls import HLSStream
from streamlink.stream.http import HTTPStream

ALIASES = {'best', 'worst', 'best-unfiltered', 'worst-unfiltered'}

FAST_DEFAULTS = {
    'twitch-supported-codecs': 'h264',
    'http-timeout': 10.0,
    'http-retries': 1,
    'hls-playlist-reload-attempts': 2,
}

_SESSION = None
_SESSION_OPTS_KEY = None


def version():
    return streamlink.__version__


def _safe_set(session, key, value, skipped):
    try:
        session.set_option(key, value)
    except Exception:
        skipped.append(str(key))


def get_session(options_dict=None):
    global _SESSION, _SESSION_OPTS_KEY
    options_dict = options_dict or {}
    key = json.dumps(options_dict, sort_keys=True, default=str)
    if _SESSION is not None and key == _SESSION_OPTS_KEY:
        return _SESSION, True
    session = Streamlink()
    skipped = []
    for k, v in FAST_DEFAULTS.items():
        _safe_set(session, k, v, skipped)
    for k, v in options_dict.items():
        _safe_set(session, k, v, skipped)
    _SESSION = session
    _SESSION_OPTS_KEY = key
    get_session.last_skipped = skipped
    return session, False


def warmup(url=None):
    import time
    t0 = time.perf_counter()
    session, reused = get_session({})
    timings = {'session_ms': (time.perf_counter() - t0) * 1000.0, 'reused': reused}
    if url:
        try:
            t1 = time.perf_counter()
            session.resolve_url(url)
            timings['resolve_ms'] = (time.perf_counter() - t1) * 1000.0
        except Exception as exc:
            timings['resolve_error'] = '%s: %s' % (type(exc).__name__, exc)
    timings['version'] = streamlink.__version__
    return json.dumps(timings)


def _headers(session, stream):
    headers = dict(session.http.headers)
    args = getattr(stream, 'args', None) or {}
    headers.update(args.get('headers') or {})
    try:
        cookies = {c.name: c.value for c in session.http.cookies}
    except Exception:
        cookies = {}
    if cookies:
        headers['Cookie'] = '; '.join(k + '=' + v for k, v in cookies.items())
    return {str(k): str(v) for k, v in headers.items()}


def list_streams(url, options_json='{}', wanted='all'):
    import time
    t_total = time.perf_counter()
    try:
        options_dict = json.loads(options_json) if options_json else {}
    except Exception:
        options_dict = {}

    t0 = time.perf_counter()
    try:
        session, reused = get_session(options_dict)
    except Exception as exc:
        return json.dumps({'error': 'unexpected',
                           'message': 'session: %s: %s' % (type(exc).__name__, exc)})
    session_ms = (time.perf_counter() - t0) * 1000.0

    t1 = time.perf_counter()
    try:
        streams = session.streams(url)
    except NoPluginError:
        return json.dumps({'error': 'no_plugin', 'message': 'No plugin matches this URL'})
    except (PluginError, StreamlinkError) as exc:
        return json.dumps({'error': 'streamlink_error', 'message': str(exc)})
    except Exception as exc:
        return json.dumps({'error': 'unexpected', 'message': type(exc).__name__ + ': ' + str(exc)})
    streams_ms = (time.perf_counter() - t1) * 1000.0

    t2 = time.perf_counter()
    materialize_all = (wanted == 'all')
    keep = None
    if not materialize_all:
        keep = {wanted, 'best', 'worst'} & set(streams.keys())

    items = []
    for name, stream in streams.items():
        if keep is not None and name not in keep:
            continue
        kind = type(stream).__name__
        direct = isinstance(stream, (HLSStream, HTTPStream))
        try:
            stream_url = stream.url if direct else None
        except Exception:
            stream_url = None
            direct = False
        items.append({
            'quality': name,
            'alias': name in ALIASES,
            'kind': kind,
            'playable_direct': direct and stream_url is not None,
            'url': stream_url if direct else None,
            'headers': _headers(session, stream) if (direct and stream_url) else {},
        })
    build_ms = (time.perf_counter() - t2) * 1000.0
    total_ms = (time.perf_counter() - t_total) * 1000.0

    payload = {
        'error': None,
        'streams': items,
        'wanted': wanted,
        'quality_index': sorted(streams.keys()) if not materialize_all else None,
        '_timings': {
            'session_ms': round(session_ms, 1),
            'streams_ms': round(streams_ms, 1),
            'build_ms': round(build_ms, 1),
            'total_ms': round(total_ms, 1),
            'session_reused': reused,
            'count': len(items),
        },
    }
    skipped = getattr(get_session, 'last_skipped', None)
    if skipped:
        payload['_timings']['skipped_options'] = skipped
    return json.dumps(payload)


if __name__ == '__main__':
    import sys
    target = sys.argv[1] if len(sys.argv) > 1 else 'hls://https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
    wanted_arg = sys.argv[2] if len(sys.argv) > 2 else 'all'
    print(list_streams(target, '{}', wanted_arg))
