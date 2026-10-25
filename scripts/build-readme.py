#!/usr/bin/env python3
"""README.src.md(고쳐 쓰는 원고) → README.md(GitHub에 보이는 파일).

GitHub는 README에 자기 CSS를 입히고 한글을 글자 사이 어디서나 줄바꿈한다(word-break: normal).
CSS를 바꿀 수 없으므로 글자에 직접 표시한다(hangul-wrap 스킬과 같은 목적).
  1. 한 어절 안의 글자 사이에 보이지 않는 줄바꿈 금지 문자(U+2060 WORD JOINER)를 넣는다.
     → 한글이 띄어쓰기 자리에서만 줄바뀐다(keep-all과 같은 효과).
  2. 「그 집」, 「두 사람」처럼 뜻이 한 덩어리인 두 어절 사이 공백을 U+00A0로 바꾼다.
코드(```, `...`), 링크 주소 ](...), HTML 태그 안, 제목 줄(#), 그림 설명(![...])은 건드리지 않는다.
    python3 scripts/build-readme.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WJ = "⁠"
NBSP = " "
HANGUL = "가-힣"
# hangul-wrap kobind.mjs와 같은 목록. 동사로 쓰이는 「온·단」은 넣지 않는다.
BINDERS = "그 이 저 두 세 네 한 몇 첫 새 옛 헌 각 매 모든 무슨 어느 어떤 이런 그런 저런 여러 다른 이번 지난 다음 아무".split()

# 건드리지 않을 조각: 코드 블록, 인라인 코드, 링크·그림 주소, HTML 태그
# 제목 줄(#)은 GitHub가 그 글자로 목차 링크 주소(#설치하기)를 만들므로 건드리지 않는다.
# 그림 설명(![...])은 화면에 보이지 않고 화면 읽기 프로그램이 읽으므로 그대로 둔다.
PROTECT = re.compile(r"```.*?```|`[^`\n]*`|!\[[^\]]*\]\([^)]*\)|\]\([^)]*\)|<[^>\n]+>|^#{1,6} [^\n]*", re.S | re.M)


def glue(text: str) -> str:
    # 한 어절(공백 없는 덩어리) 안에서 한글이 낀 글자 사이마다 WJ
    def word(m: re.Match) -> str:
        w = m.group(0)
        if not re.search(f"[{HANGUL}]", w):
            return w
        out = [w[0]]
        for a, b in zip(w, w[1:]):
            if re.match(f"[{HANGUL}]", a) or re.match(f"[{HANGUL}]", b):
                if not (a in "([{" or b in ")]}"):
                    out.append(WJ)
            out.append(b)
        return "".join(out)

    text = re.sub(r"[^\s|*_#>\[\]!]+", word, text)
    # 굵게(**)·기울임(_)이 어절 한가운데 있으면(예: **루틴**에) 그 경계에도 WJ를 넣는다.
    text = re.sub(rf"(?<=[{HANGUL}\w.)])(\*\*|_)(?=[{HANGUL}])", rf"\1{WJ}", text)
    text = re.sub(rf"(?<=[{HANGUL}])(\*\*|_)(?=[{HANGUL}\w(])", rf"{WJ}\1", text)
    # 두 어절 묶기: 기대는 말 + 공백 + 한글
    for b in BINDERS:
        pat = WJ.join(b)
        # 앞이 한글이면 어절 끝(예: 알람이)이라 묶지 않는다. 따옴표 뒤 WJ(예: "이 출처)는 괜찮다.
        text = re.sub(rf"(?<![{HANGUL}])(?<![{HANGUL}]{WJ})({re.escape(pat)}) (?=[{HANGUL}])", rf"\1{NBSP}", text)
    return text


def build(src: str) -> str:
    out, last = [], 0
    for m in PROTECT.finditer(src):
        out.append(glue(src[last:m.start()]))
        out.append(m.group(0))
        last = m.end()
    out.append(glue(src[last:]))
    head = "<!-- 이 파일은 scripts/build-readme.py가 README.src.md에서 만든다. 고칠 때는 README.src.md를 고친다. -->\n"
    return head + "".join(out)


if __name__ == "__main__":
    src = (ROOT / "README.src.md").read_text()
    (ROOT / "README.md").write_text(build(src))
    plain = (ROOT / "README.md").read_text().replace(WJ, "").replace(NBSP, " ")
    assert plain.split("\n", 1)[1] == src, "WJ·NBSP를 빼면 원고와 같아야 한다"
    print("README.md", (ROOT / "README.md").stat().st_size, "bytes")
