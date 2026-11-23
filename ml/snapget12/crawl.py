"""
Nguon anh Internet cho tap VN-train (ml/DATASET_SPEC.md muc G) — dung boi scripts/17_crawl_autolabel.py.

  openverse  API chinh thuc, anh Creative Commons (Flickr, Wikimedia...) — CO license de trich nguon bao cao
  wikimedia  Wikimedia Commons API (CC) — CO license
  ddg        DuckDuckGo Images (thu vien `ddgs`) — ket qua giong cong cu tim kiem thuong, ho tro tieng Viet
             (region vn-vi); license khong ro -> ghi "unknown". Chi dung hoc thuat, KHONG phat tan lai anh.
  --- them 2026-11-24 (CAN API KEY mien phi, dat trong bien moi truong / Colab Secrets) ---
  serpapi    GOOGLE IMAGES qua SerpApi (SERPAPI_KEY) — cach hop phap de lay ket qua Google; tim tieng Viet, gl=vn
  flickr     Flickr API (FLICKR_API_KEY) — anh CC/PD that, nhieu anh du lich/doi thuong VN, co license
  pexels     Pexels API (PEXELS_API_KEY) — anh chat luong cao, Pexels License
  pixabay    Pixabay API (PIXABAY_API_KEY) — Pixabay License, anh lon nhat 1280px

KHONG cao truc tiep Google Images: Google chan request tu dong (dac biet IP Colab -> CAPTCHA/ket qua rong)
va dieu khoan Google cam. Google Custom Search JSON API DA DONG voi khach hang moi (2026) va ngung han
01/01/2027 -> dung SerpApi (dich vu tra ket qua Google Images qua API).
Nguon thieu key -> bo qua kem thong bao (khong lam hong ca lan chay).

Moi request: User-Agent chung (khong chua thong tin ca nhan), timeout, gioi han dung luong, lui lai khi 429.
"""
from __future__ import annotations

import io
import os
import re
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field

USER_AGENT = "SnapgetDatasetBuilder/1.0 (educational computer-vision project; python-requests)"
MAX_BYTES = 15 * 1024 * 1024

# Ten mien hay ra anh CO WATERMARK / LOGO / CHU CHEN (DATASET_SPEC muc E cam): kho anh stock + san TMDT
# (anh san pham gan logo shop, chu quang cao — gap ngay lan chay thu dau tien 2026-11-23).
BLOCKED_DOMAINS = (
    "shutterstock.com", "alamy.com", "dreamstime.com", "123rf.com", "istockphoto.com", "gettyimages.",
    "depositphotos.com", "ftcdn.net", "bigstockphoto.com", "canstockphoto.com", "vecteezy.com", "vectorstock.com",
    "agefotostock.com", "pond5.com", "stock.adobe.com",
    "susercontent.com", "shopee.", "hstatic.net", "lazcdn.com", "lazada.", "tiki.vn", "tikicdn.com", "sendo.vn",
    "salt.tikicdn.com", "aliexpress.", "alicdn.com", "amazon.", "media-amazon.com", "ebay.", "ebayimg.com",
)


def blocked(cand: "Candidate") -> bool:
    text = f"{cand.url} {cand.page}".lower()
    return any(d in text for d in BLOCKED_DOMAINS)


@dataclass
class Candidate:
    url: str                      # link anh goc
    provider: str                 # openverse | wikimedia | ddg
    page: str = ""                # trang goc (landing) — de trich nguon
    license: str = "unknown"
    creator: str = ""
    title: str = ""
    width: int = 0
    height: int = 0
    extra: dict = field(default_factory=dict)


def set_contact(contact: str | None) -> None:
    """Wikimedia khuyen nghi User-Agent co thong tin lien he (giam bi 429). Mac dinh KHONG them gi —
    chi them khi nguoi dung tu truyen --contact (vd email/URL repo cua ho)."""
    global USER_AGENT
    if contact:
        USER_AGENT = f"SnapgetDatasetBuilder/1.0 ({contact}; educational computer-vision project)"


def _session():
    import requests

    s = requests.Session()
    s.headers.update({"User-Agent": USER_AGENT})
    return s


class KeyRejected(PermissionError):
    """API key sai / het quota — tat han nguon trong lan chay nay."""


class MissingKey(Exception):
    pass


def _key(env: str) -> str:
    v = os.environ.get(env, "").strip()
    if not v:
        raise MissingKey(env)
    return v


def _get_json(session, url: str, params: dict, headers: dict | None = None, retries: int = 4):
    for attempt in range(retries):
        try:
            r = session.get(url, params=params, headers=headers or {}, timeout=30)
        except Exception as e:  # timeout / mat mang -> thu lai
            print(f"   ({url.split('/')[2]} loi {type(e).__name__} — thu lai)")
            time.sleep(5 * (attempt + 1))
            continue
        if r.status_code == 429:  # bi gioi han toc do -> doi roi thu lai
            wait = float(r.headers.get("Retry-After", 0) or 0) or 15 * (attempt + 1)
            print(f"   (429 tu {url.split('/')[2]} — doi {wait:.0f}s)")
            time.sleep(min(wait, 120))
            continue
        if r.status_code >= 500:  # loi phia server (Openverse hay 504) -> thu lai
            print(f"   ({r.status_code} tu {url.split('/')[2]} — thu lai)")
            time.sleep(5 * (attempt + 1))
            continue
        if r.status_code in (401, 403):  # key sai / het quota -> khong thu lai vo ich, script 17 tat nguon nay
            raise KeyRejected(f"{url.split('/')[2]}: HTTP {r.status_code} — API key sai hoac het quota")
        r.raise_for_status()
        return r.json()
    raise RuntimeError(f"{url}: van loi (429/5xx/mang) sau {retries} lan")


# ---------------------------------------------------------------------------
class OpenverseSource:
    """https://api.openverse.org — an danh duoc (gioi han thap); co OPENVERSE_CLIENT_ID/SECRET thi xin token (gioi han cao hon)."""

    name = "openverse"
    lang = "en"
    API = "https://api.openverse.org/v1"

    def __init__(self) -> None:
        self.s = _session()
        self.headers: dict = {}
        cid, sec = os.environ.get("OPENVERSE_CLIENT_ID"), os.environ.get("OPENVERSE_CLIENT_SECRET")
        if cid and sec:
            r = self.s.post(f"{self.API}/auth_tokens/token/", timeout=20,
                            data={"grant_type": "client_credentials", "client_id": cid, "client_secret": sec})
            r.raise_for_status()
            self.headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
        self.page_size = 50 if self.headers else 20

    def search(self, query: str, limit: int) -> list[Candidate]:
        out: list[Candidate] = []
        page = 1
        while len(out) < limit and page <= 10:
            # KHONG loc size/category: phan lon anh Openverse thieu 2 metadata nay -> loc la mat ~90% ket qua
            # (do 2026-11-23: "plastic stool Vietnam" 26 -> 2-3). Kich thuoc da loc bang --min-side o script 17.
            # Openverse hay 504 (qua tai phia ho) -> chi thu lai 2 lan, va loi o trang sau van GIU ket qua trang truoc
            try:
                data = _get_json(self.s, f"{self.API}/images/", {
                    "q": query, "page": page, "page_size": self.page_size, "mature": "false",
                }, self.headers, retries=2)
            except Exception:
                if out:
                    break
                raise
            res = data.get("results", [])
            for it in res:
                lic = f"CC {it.get('license', '').upper()} {it.get('license_version') or ''}".strip()
                out.append(Candidate(url=it["url"], provider=self.name, page=it.get("foreign_landing_url", ""),
                                     license=lic, creator=it.get("creator") or "", title=it.get("title") or "",
                                     width=it.get("width") or 0, height=it.get("height") or 0,
                                     extra={"source": it.get("source", "")}))
            if len(res) < self.page_size or page >= data.get("page_count", 1):
                break
            page += 1
            time.sleep(1.0)
        return out[:limit]


class WikimediaSource:
    name = "wikimedia"
    lang = "en"
    API = "https://commons.wikimedia.org/w/api.php"

    def __init__(self, thumb_width: int = 2048) -> None:
        self.s = _session()
        self.thumb_width = thumb_width

    def search(self, query: str, limit: int) -> list[Candidate]:
        out: list[Candidate] = []
        offset = 0
        while len(out) < limit and offset < 500:
            data = _get_json(self.s, self.API, {
                "action": "query", "format": "json", "generator": "search", "gsrnamespace": 6,
                "gsrsearch": f"{query} filetype:bitmap", "gsrlimit": 50, "gsroffset": offset,
                "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": self.thumb_width,
            })
            pages = (data.get("query") or {}).get("pages", {})
            for p in pages.values():
                ii = (p.get("imageinfo") or [{}])[0]
                if ii.get("mime") not in ("image/jpeg", "image/png", "image/webp"):
                    continue
                meta = ii.get("extmetadata") or {}
                url = ii.get("thumburl") if ii.get("width", 0) > self.thumb_width else ii.get("url")
                out.append(Candidate(url=url, provider=self.name, page=ii.get("descriptionurl", ""),
                                     license=(meta.get("LicenseShortName") or {}).get("value", "unknown"),
                                     creator=re.sub(r"<[^>]+>", "", (meta.get("Artist") or {}).get("value", "")).strip(),
                                     title=p.get("title", ""), width=ii.get("width", 0), height=ii.get("height", 0)))
            cont = data.get("continue", {}).get("gsroffset")
            if not cont:
                break
            offset = cont
            time.sleep(0.5)
        return out[:limit]


class DuckDuckGoSource:
    """Thu vien `ddgs` (ten cu `duckduckgo_search`) — API thay doi theo phien ban nen goi tham so vi tri."""

    name = "ddg"
    lang = "vi"

    def __init__(self, region: str = "vn-vi", license_image: str | None = None, pause: float = 2.0) -> None:
        try:
            from ddgs import DDGS
        except ImportError:
            from duckduckgo_search import DDGS  # phien ban cu
        self.DDGS = DDGS
        self.region = region
        self.license_image = license_image  # None = moi license | "Share" | "ShareCommercially" | "Modify" | ...
        self.pause = pause

    def search(self, query: str, limit: int) -> list[Candidate]:
        kw = {"region": self.region, "safesearch": "moderate", "size": "Large", "max_results": limit}
        if self.license_image:
            kw["license_image"] = self.license_image
        for attempt in range(3):
            try:
                res = list(self.DDGS().images(query, **kw) or [])
                break
            except Exception as e:  # rate limit / mang
                print(f"   (ddg loi {type(e).__name__}: {e} — thu lai)")
                time.sleep(10 * (attempt + 1))
        else:
            return []
        time.sleep(self.pause)  # lich su — khong ban request lien tuc
        return [Candidate(url=r.get("image", ""), provider=self.name, page=r.get("url", ""),
                          license=f"ddg:{self.license_image}" if self.license_image else "unknown",
                          title=r.get("title", ""), width=int(r.get("width") or 0), height=int(r.get("height") or 0),
                          extra={"site": r.get("source", "")}) for r in res if r.get("image")]


class SerpApiGoogleSource:
    """GOOGLE IMAGES qua SerpApi (https://serpapi.com, engine google_images). 1 lan tim = ~100 anh = 1 luot quota
    (goi free gioi han so luot/thang — xem trang SerpApi). Tim tieng Viet, gl=vn -> dung boi canh VN nhat."""

    name = "serpapi"
    lang = "vi"
    API = "https://serpapi.com/search.json"

    def __init__(self) -> None:
        self.key = _key("SERPAPI_KEY")
        self.s = _session()

    @staticmethod
    def parse(data: dict) -> list[Candidate]:
        if data.get("error") and "images_results" not in data:
            msg = str(data["error"])
            if "key" in msg.lower() or "run out" in msg.lower() or "limit" in msg.lower():
                raise KeyRejected(f"serpapi: {msg}")
            return []  # vd "Google hasn't returned any results"
        out = []
        for it in data.get("images_results", []):
            if not it.get("original"):
                continue
            out.append(Candidate(url=it["original"], provider="serpapi", page=it.get("link", ""), license="unknown",
                                 title=it.get("title", ""), width=int(it.get("original_width") or 0),
                                 height=int(it.get("original_height") or 0), extra={"site": it.get("source", "")}))
        return out

    def search(self, query: str, limit: int) -> list[Candidate]:
        out: list[Candidate] = []
        page = 0
        while len(out) < limit and page < 3:  # moi trang ~100 anh -> thuong 1 trang la du (tiet kiem quota)
            data = _get_json(self.s, self.API, {"engine": "google_images", "q": query, "gl": "vn", "hl": "vi",
                                                "ijn": page, "safe": "active", "tbs": "isz:l", "api_key": self.key},
                             retries=2)
            got = self.parse(data)
            out += got
            if len(got) < 50:
                break
            page += 1
        return out[:limit]


FLICKR_LICENSES = {"1": "CC BY-NC-SA 2.0", "2": "CC BY-NC 2.0", "3": "CC BY-NC-ND 2.0", "4": "CC BY 2.0",
                   "5": "CC BY-SA 2.0", "6": "CC BY-ND 2.0", "7": "No known copyright", "8": "US Government Work",
                   "9": "CC0 1.0", "10": "Public Domain Mark 1.0"}


class FlickrSource:
    """Flickr API (key mien phi: https://www.flickr.com/services/apps/create/). Chi lay anh CC / public domain.
    vn_only=True: chi anh gan toa do trong lanh tho VN (it anh hon nhung chac boi canh VN)."""

    name = "flickr"
    lang = "en"
    API = "https://www.flickr.com/services/rest/"
    VN_BBOX = "102.1,8.4,109.5,23.4"

    def __init__(self, vn_only: bool = False) -> None:
        self.key = _key("FLICKR_API_KEY")
        self.s = _session()
        self.vn_only = vn_only

    @staticmethod
    def parse(data: dict) -> list[Candidate]:
        if data.get("stat") == "fail":
            if data.get("code") in (100, 105):  # 100 = key sai
                raise KeyRejected(f"flickr: {data.get('message')}")
            return []
        out = []
        for p in (data.get("photos") or {}).get("photo", []):
            # uu tien anh goc (neu khong qua lon), roi url_l (canh dai 1024), roi url_c (800 -> se bi loc)
            for suf in ("o", "l", "c"):
                u, w, h = p.get(f"url_{suf}"), int(p.get(f"width_{suf}") or 0), int(p.get(f"height_{suf}") or 0)
                if u and (suf != "o" or max(w, h) <= 6000):
                    break
            else:
                continue
            out.append(Candidate(url=u, provider="flickr", page=f"https://www.flickr.com/photos/{p.get('owner')}/{p.get('id')}",
                                 license=FLICKR_LICENSES.get(str(p.get("license")), "unknown"),
                                 creator=p.get("ownername", ""), title=p.get("title", ""), width=w, height=h))
        return out

    def search(self, query: str, limit: int) -> list[Candidate]:
        params = {"method": "flickr.photos.search", "api_key": self.key, "text": query, "license": ",".join(FLICKR_LICENSES),
                  "content_type": 1, "media": "photos", "safe_search": 1, "sort": "relevance", "per_page": min(limit * 2, 250),
                  "extras": "url_o,url_l,url_c,owner_name,license", "format": "json", "nojsoncallback": 1}
        if self.vn_only:
            params["bbox"] = self.VN_BBOX
        return self.parse(_get_json(self.s, self.API, params, retries=3))[:limit]


class PexelsSource:
    """Pexels API (key mien phi: https://www.pexels.com/api/). Pexels License (dung tu do)."""

    name = "pexels"
    lang = "en"
    API = "https://api.pexels.com/v1/search"

    def __init__(self) -> None:
        self.headers = {"Authorization": _key("PEXELS_API_KEY")}
        self.s = _session()

    @staticmethod
    def parse(data: dict) -> list[Candidate]:
        out = []
        for p in data.get("photos", []):
            src = p.get("src") or {}
            u = src.get("large2x") or src.get("original")  # large2x ~1880px: du net, nhe hon anh goc nhieu MB
            if u:
                out.append(Candidate(url=u, provider="pexels", page=p.get("url", ""), license="Pexels License",
                                     creator=p.get("photographer", ""), title=p.get("alt", ""),
                                     width=int(p.get("width") or 0), height=int(p.get("height") or 0)))
        return out

    def search(self, query: str, limit: int) -> list[Candidate]:
        data = _get_json(self.s, self.API, {"query": query, "per_page": min(limit, 80), "page": 1}, self.headers, retries=3)
        return self.parse(data)[:limit]


class PixabaySource:
    """Pixabay API (key mien phi: https://pixabay.com/api/docs/). Pixabay License. largeImageURL toi da 1280px."""

    name = "pixabay"
    lang = "en"
    API = "https://pixabay.com/api/"

    def __init__(self) -> None:
        self.key = _key("PIXABAY_API_KEY")
        self.s = _session()

    @staticmethod
    def parse(data: dict) -> list[Candidate]:
        return [Candidate(url=h["largeImageURL"], provider="pixabay", page=h.get("pageURL", ""), license="Pixabay License",
                          creator=h.get("user", ""), title=h.get("tags", ""),
                          # largeImageURL thu nho canh dai ve 1280 -> ghi kich thuoc THAT se tai ve
                          width=round(int(h.get("imageWidth") or 0) * min(1, 1280 / max(int(h.get("imageWidth") or 1), int(h.get("imageHeight") or 1)))),
                          height=round(int(h.get("imageHeight") or 0) * min(1, 1280 / max(int(h.get("imageWidth") or 1), int(h.get("imageHeight") or 1)))))
                for h in data.get("hits", []) if h.get("largeImageURL")]

    def search(self, query: str, limit: int) -> list[Candidate]:
        data = _get_json(self.s, self.API, {"key": self.key, "q": query[:100], "image_type": "photo", "safesearch": "true",
                                            "per_page": max(3, min(limit, 200)), "min_width": 1024}, retries=3)
        return self.parse(data)[:limit]


ALL_SOURCES = ("serpapi", "flickr", "pexels", "pixabay", "ddg", "wikimedia", "openverse")  # thu tu "auto"
KEY_ENV = {"serpapi": "SERPAPI_KEY", "flickr": "FLICKR_API_KEY", "pexels": "PEXELS_API_KEY", "pixabay": "PIXABAY_API_KEY"}


def make_sources(names: list[str], ddg_license: str | None = None, flickr_vn_only: bool = False) -> list:
    """names co the chua 'auto' = moi nguon co key (+ ddg, wikimedia, openverse). Nguon thieu key -> bo qua + bao."""
    table = {"openverse": OpenverseSource, "wikimedia": WikimediaSource,
             "ddg": lambda: DuckDuckGoSource(license_image=ddg_license),
             "serpapi": SerpApiGoogleSource, "flickr": lambda: FlickrSource(vn_only=flickr_vn_only),
             "pexels": PexelsSource, "pixabay": PixabaySource}
    expanded: list[str] = []
    for n in names:
        expanded += list(ALL_SOURCES) if n == "auto" else [n]
    out = []
    for n in dict.fromkeys(expanded):
        if n not in table:
            raise SystemExit(f"!! nguon '{n}' khong ho tro ({' | '.join(ALL_SOURCES)} | auto)")
        try:
            out.append(table[n]())
        except MissingKey as e:
            print(f"   (bo qua nguon '{n}': chua co bien moi truong {e} — xem o 2 notebook)")
        except ImportError as e:
            print(f"   (bo qua nguon '{n}': thieu thu vien {e.name})")
    if not out:
        raise SystemExit("!! khong co nguon anh nao dung duoc")
    print("Nguon anh:", ", ".join(s.name for s in out))
    return out


# ---------------------------------------------------------------------------
def download_one(session, cand: Candidate):
    """-> (cand, PIL.Image da xoay EXIF + RGB) hoac (cand, 'ly do loi')."""
    from PIL import Image, ImageOps

    if not cand.url.startswith(("http://", "https://")):
        return cand, "url khong hop le"
    try:
        for attempt in range(3):  # 429/503 (Wikimedia upload, Flickr gioi han toc do) -> doi roi thu lai
            r = session.get(cand.url, timeout=20, stream=True)
            if r.status_code not in (429, 503):
                break
            r.close()
            time.sleep(float(r.headers.get("Retry-After", 0) or 0) or 4 * (attempt + 1))
        if r.status_code != 200:
            return cand, f"HTTP {r.status_code}"
        ctype = r.headers.get("Content-Type", "")
        if ctype and not ctype.startswith("image/"):
            return cand, f"khong phai anh ({ctype.split(';')[0]})"
        buf = bytearray()
        for chunk in r.iter_content(65536):
            buf.extend(chunk)
            if len(buf) > MAX_BYTES:
                return cand, "file qua lon"
        img = Image.open(io.BytesIO(bytes(buf)))
        img = ImageOps.exif_transpose(img).convert("RGB")
        return cand, img
    except Exception as e:
        return cand, f"loi tai: {type(e).__name__}"


def download_many(cands: list[Candidate], workers: int = 8):
    s = _session()
    with ThreadPoolExecutor(max_workers=workers) as ex:
        return list(ex.map(lambda c: download_one(s, c), cands))
