#!/usr/bin/env python3
"""날짜별 상태바 알림 아이콘 벡터 31개를 생성한다.

상태바 알림 아이콘(ic_notification) 안에 오늘 날짜 숫자를 넣은 변형을
1일~31일분으로 res/drawable/ic_notification_day_<날짜>.xml 에 내보낸다.
Android는 런처·알림 아이콘을 런타임에 다시 그릴 수 없으므로 날짜별 리소스를
미리 만들어두고, AgendaNotificationManager가 알림 발행 시점에 오늘 날짜 것을 고른다.

단일 출처: 캘린더 프레임 경로(FRAME_PATH_DATA)와 숫자 모양(digitPath)은 이 스크립트에만
정의되어 있다. 생성된 XML은 산출물이므로 직접 수정하지 말고 이 스크립트를 고친 뒤
다시 실행한다 (repo 루트에서 `python3 tools/generate_notification_icons.py`).

숫자 모양: 모서리를 둥글린 연속 스트로크 골격. 24dp에서 읽기 좋도록 굵은 획(1.7)에
둥근 끝단을 쓴다. 한 자리 날짜는 같은 높이에 더 넓은 칸으로 가운데 정렬한다.
"""

from pathlib import Path

RES_DRAWABLE_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

# 기존 ic_notification.xml의 캘린더 프레임(위 탭 2개 + 2 두께 테두리 + 빈 창)을 그대로 쓴다.
# 창 영역이 (4,8)-(20,21)이라 날짜 숫자가 들어갈 자리가 된다.
FRAME_PATH_DATA = (
    "M20,3h-1L19,1h-2v2L7,3L7,1L5,1v2L4,3c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2"
    "L22,5c0,-1.1 -0.9,-2 -2,-2zM20,21L4,21L4,8h16v13zM20,6L4,6L4,5h16v6z"
)

STROKE_WIDTH = 1.7
STROKE_HALF = STROKE_WIDTH / 2

# 창 (4,8)-(20,21) 안에서 숫자 세로 폭은 9, 세로로 가운데(14.5 기준)에 둔다.
DIGIT_HEIGHT = 9.0
DIGIT_TOP_OFFSET = 14.5 - DIGIT_HEIGHT / 2  # = 10.0

TWO_DIGIT_CELL_WIDTH = 5.6
SINGLE_DIGIT_CELL_WIDTH = 7.4
TWO_DIGIT_GAP = 1.2

CORNER_RADIUS = 1.15


def rounded_rect_path(left: float, top: float, right: float, bottom: float) -> str:
    """모서리를 둥글린 사각형 윤곽(0과 8의 루프에 쓴다). 좌표는 획 중심선 기준."""
    radius = CORNER_RADIUS
    return (
        f"M{left:.2f},{top + radius:.2f} "
        f"A{radius},{radius} 0 0 1 {left + radius:.2f},{top:.2f} "
        f"L{right - radius:.2f},{top:.2f} "
        f"A{radius},{radius} 0 0 1 {right:.2f},{top + radius:.2f} "
        f"L{right:.2f},{bottom - radius:.2f} "
        f"A{radius},{radius} 0 0 1 {right - radius:.2f},{bottom:.2f} "
        f"L{left + radius:.2f},{bottom:.2f} "
        f"A{radius},{radius} 0 0 1 {left:.2f},{bottom - radius:.2f} Z"
    )


def digit_path(digit: str, cell_origin_x: float, cell_width: float) -> str:
    """한 자리 숫자의 스트로크 경로. 셀 왼쪽 위 좌표에 놓는다. 좌표는 획 중심선 기준."""
    x0 = cell_origin_x + STROKE_HALF
    x1 = cell_origin_x + cell_width - STROKE_HALF
    y0 = DIGIT_TOP_OFFSET + STROKE_HALF
    y1 = DIGIT_TOP_OFFSET + DIGIT_HEIGHT - STROKE_HALF
    xm = DIGIT_TOP_OFFSET + DIGIT_HEIGHT / 2
    radius = CORNER_RADIUS

    if digit == "0":
        return rounded_rect_path(x0, y0, x1, y1)
    if digit == "1":
        flag_top_x = cell_origin_x + cell_width / 2
        return (
            f"M{flag_top_x - 1.3:.2f},{y0 + 1.6:.2f} "
            f"L{flag_top_x:.2f},{y0:.2f} V{y1:.2f} "
            f"M{flag_top_x - 1.2:.2f},{y1:.2f} H{flag_top_x + 1.2:.2f}"
        )
    if digit == "2":
        return (
            f"M{x0:.2f},{y0 + radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x0 + radius:.2f},{y0:.2f} "
            f"L{x1 - radius:.2f},{y0:.2f} "
            f"A{radius},{radius} 0 0 1 {x1:.2f},{y0 + radius:.2f} "
            f"L{x1:.2f},{xm:.2f} "
            f"L{x0:.2f},{y1:.2f} H{x1:.2f}"
        )
    if digit == "3":
        return (
            f"M{x0:.2f},{y0 + radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x0 + radius:.2f},{y0:.2f} "
            f"L{x1 - radius:.2f},{y0:.2f} "
            f"A{radius},{radius} 0 0 1 {x1:.2f},{y0 + radius:.2f} "
            f"L{x1:.2f},{xm - radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x1 - radius:.2f},{xm:.2f} "
            f"M{x0 + radius:.2f},{xm:.2f} H{x1 - radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x1:.2f},{xm + radius:.2f} "
            f"L{x1:.2f},{y1 - radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x1 - radius:.2f},{y1:.2f} "
            f"L{x0 + radius:.2f},{y1:.2f}"
        )
    if digit == "4":
        return f"M{x1:.2f},{y1:.2f} V{y0:.2f} L{x0:.2f},{xm + 0.7:.2f} H{x1:.2f}"
    if digit == "5":
        return (
            f"M{x1:.2f},{y0:.2f} H{x0 + radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x0:.2f},{y0 + radius:.2f} "
            f"L{x0:.2f},{xm - radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x0 + radius:.2f},{xm:.2f} "
            f"L{x1 - radius:.2f},{xm:.2f} "
            f"A{radius},{radius} 0 0 1 {x1:.2f},{xm + radius:.2f} "
            f"L{x1:.2f},{y1 - radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x1 - radius:.2f},{y1:.2f} "
            f"L{x0 + radius:.2f},{y1:.2f}"
        )
    if digit == "6":
        return (
            f"M{x1:.2f},{y0 + radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x1 - radius:.2f},{y0:.2f} "
            f"L{x0 + radius:.2f},{y0:.2f} "
            f"A{radius},{radius} 0 0 0 {x0:.2f},{y0 + radius:.2f} "
            f"L{x0:.2f},{y1 - radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x0 + radius:.2f},{y1:.2f} "
            f"L{x1 - radius:.2f},{y1:.2f} "
            f"A{radius},{radius} 0 0 0 {x1:.2f},{y1 - radius:.2f} "
            f"L{x1:.2f},{xm + radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x1 - radius:.2f},{xm:.2f} "
            f"L{x0 + radius:.2f},{xm:.2f}"
        )
    if digit == "7":
        # 오른쪽 위는 arc 없이 한 번에 꺾는다. arc를 넣으면 짧은 세로 구간이 끼어
        # 갈고리처럼 보였다. 둥근 join이 모서리를 부드럽게 만든다.
        return (
            f"M{x0:.2f},{y0 + radius:.2f} "
            f"A{radius},{radius} 0 0 1 {x0 + radius:.2f},{y0:.2f} "
            f"L{x1:.2f},{y0:.2f} "
            f"L{x0 + 1.2:.2f},{y1:.2f}"
        )
    if digit == "8":
        middle_top = xm - 0.1
        middle_bottom = xm + 0.1
        return (
            f"{rounded_rect_path(x0, y0, x1, middle_top)} "
            f"{rounded_rect_path(x0, middle_bottom, x1, y1)}"
        )
    if digit == "9":
        return (
            f"M{x0:.2f},{y1 - radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x0 + radius:.2f},{y1:.2f} "
            f"L{x1 - radius:.2f},{y1:.2f} "
            f"A{radius},{radius} 0 0 0 {x1:.2f},{y1 - radius:.2f} "
            f"L{x1:.2f},{y0 + radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x1 - radius:.2f},{y0:.2f} "
            f"L{x0 + radius:.2f},{y0:.2f} "
            f"A{radius},{radius} 0 0 0 {x0:.2f},{y0 + radius:.2f} "
            f"L{x0:.2f},{xm - radius:.2f} "
            f"A{radius},{radius} 0 0 0 {x0 + radius:.2f},{xm:.2f} "
            f"L{x1 - radius:.2f},{xm:.2f}"
        )
    raise ValueError(f"모르는 숫자다: {digit}")


def day_number_path_data(day: int) -> str:
    """날짜 숫자(1~31)를 창 너비(4~20)에 가운데 정렬한 스트로크 경로."""
    digits = str(day)
    if len(digits) == 1:
        offset_x = (24 - SINGLE_DIGIT_CELL_WIDTH) / 2
        return digit_path(digits, offset_x, SINGLE_DIGIT_CELL_WIDTH)
    total_width = TWO_DIGIT_CELL_WIDTH * 2 + TWO_DIGIT_GAP
    offset_x = (24 - total_width) / 2
    first_path = digit_path(digits[0], offset_x, TWO_DIGIT_CELL_WIDTH)
    second_path = digit_path(
        digits[1], offset_x + TWO_DIGIT_CELL_WIDTH + TWO_DIGIT_GAP, TWO_DIGIT_CELL_WIDTH
    )
    return f"{first_path} {second_path}"


def build_icon_xml(day: int) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- 상태바 알림 아이콘({day}일용): tools/generate_notification_icons.py가 생성했다. 직접 수정하지 않는다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{FRAME_PATH_DATA}" />
    <path
        android:pathData="{day_number_path_data(day)}"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="{STROKE_WIDTH}" />
</vector>
"""


def main() -> None:
    for day in range(1, 32):
        target = RES_DRAWABLE_DIR / f"ic_notification_day_{day}.xml"
        target.write_text(build_icon_xml(day), encoding="utf-8")
    print(f"{31}개 아이콘을 {RES_DRAWABLE_DIR} 에 생성했다.")


if __name__ == "__main__":
    main()
