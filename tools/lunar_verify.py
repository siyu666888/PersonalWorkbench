# -*- coding: utf-8 -*-
"""农历算法验证：用经典 lunarInfo 表实现公历->农历，并用权威锚点校验。"""
from datetime import date, timedelta

lunarInfo = [
    0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,  # 1900-1909
    0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,  # 1910-1919
    0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,  # 1920-1929
    0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,  # 1930-1939
    0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,  # 1940-1949
    0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,  # 1950-1959
    0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,  # 1960-1969
    0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,  # 1970-1979
    0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,  # 1980-1989
    0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,  # 1990-1999
    0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,  # 2000-2009
    0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,  # 2010-2019
    0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,  # 2020-2029
    0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x0d0b6,0x0d25f,0x0d520,0x0dd45,  # 2030-2039
    0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x0b255,0x06d2f,0x0ada0,  # 2040-2049
    0x14b63,0x0937f,0x049f8,0x04970,0x064b0,0x068a6,0x0ea5f,0x06b20,0x0a6c4,0x0aaef,  # 2050-2059
    0x092e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,  # 2060-2069
    0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,  # 2070-2079
    0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b6f,0x0a570,0x054e4,0x0d260,  # 2080-2089
    0x0e968,0x0d520,0x0daa0,0x06aa6,0x056df,0x04ae0,0x0a9d4,0x0a4d0,0x0d150,0x0f252,  # 2090-2099
    0x0d520,  # 2100
]
assert len(lunarInfo) == 201, len(lunarInfo)


def leap_month(y):
    return lunarInfo[y - 1900] & 0xf


def leap_days(y):
    if leap_month(y):
        return 30 if (lunarInfo[y - 1900] & 0x10000) else 29
    return 0


def month_days(y, m):
    return 30 if (lunarInfo[y - 1900] & (0x10000 >> m)) else 29


def lunar_year_days(y):
    s = 348
    i = 0x8000
    while i > 0x8:
        if lunarInfo[y - 1900] & i:
            s += 1
        i >>= 1
    return s + leap_days(y)


# 公历->农历 (1900-01-31 = 农历1900年正月初一)
BASE = date(1900, 1, 31)


def solar_to_lunar(d: date):
    offset = (d - BASE).days
    y = 1900
    while y <= 2100:
        dy = lunar_year_days(y)
        if offset < dy:
            break
        offset -= dy
        y += 1
    lm = leap_month(y)
    leap = False
    m = 1
    while m <= 12:
        if lm > 0 and m == lm + 1 and not leap:
            days = leap_days(y)
            leap = True
        else:
            days = month_days(y, m)
        if offset < days:
            break
        offset -= days
        if not (lm > 0 and m == lm + 1 and leap and m == lm + 1 and False):
            pass
        if not leap or m != lm:
            if not (leap and m == lm + 1):
                m += 1
        else:
            m += 1
    return y, m, offset + 1, leap


# 更简洁可靠的展开法（逐月推进）
def solar_to_lunar2(d: date):
    offset = (d - BASE).days
    y = 1900
    while True:
        dy = lunar_year_days(y)
        if offset < dy:
            break
        offset -= dy
        y += 1
    lm = leap_month(y)
    m = 1
    is_leap = False
    leap_done = False
    while True:
        # 闰月标签为 lm（如 2025 年闰六月），它插在 lm 月与 lm+1 月之间
        use_leap = (lm > 0 and m == lm + 1 and not leap_done)
        days = leap_days(y) if use_leap else month_days(y, m)
        if offset < days:
            is_leap = use_leap
            if use_leap:
                m = lm
            break
        offset -= days
        if use_leap:
            leap_done = True     # 闰月已消费，m 不变，下一轮处理正式 m 月
        else:
            m += 1
        if m > 12:
            raise ValueError(f"month overflow at {d}")
    return y, m, offset + 1, is_leap


DAY_CN = ['初一','初二','初三','初四','初五','初六','初七','初八','初九','初十',
          '十一','十二','十三','十四','十五','十六','十七','十八','十九','二十',
          '廿一','廿二','廿三','廿四','廿五','廿六','廿七','廿八','廿九','三十']
MONTH_CN = ['','正','二','三','四','五','六','七','八','九','十','冬','腊']
GAN = list('甲乙丙丁戊己庚辛壬癸')
ZHI = list('子丑寅卯辰巳午未申酉戌亥')
ZODIAC = list('鼠牛虎兔龙蛇马羊猴鸡狗猪')


def fmt(d: date):
    y, m, dd, lp = solar_to_lunar2(d)
    gan = GAN[(y - 4) % 10]
    zhi = ZHI[(y - 4) % 12]
    return (f"{d} 农历{y}年{'闰' if lp else ''}{MONTH_CN[m]}月{DAY_CN[dd-1]} "
            f"{gan}{zhi}年({ZODIAC[(y-4)%12]})")


# ===== 节气 =====
sTermInfo = [0,21208,42467,63836,85337,107014,128867,150921,173149,195551,218072,240693,
             263343,285989,308563,331033,353350,375494,397447,419210,440795,462224,483532,504758]
TERM_NAMES = ['小寒','大寒','立春','雨水','惊蛰','春分','清明','谷雨','立夏','小满','芒种','夏至',
              '小暑','大暑','立秋','处暑','白露','秋分','寒露','霜降','立冬','小雪','大雪','冬至']


def term_day(y, n):
    """第 n 个节气 (1..24) 所在公历日期"""
    base = date(1900, 1, 6).toordinal()
    # 1900-01-06 02:05
    ms = (base - date(1970,1,1).toordinal()) * 86400000 + (2*3600 + 5*60) * 1000
    ms += int(31556925974.7 * (y - 1900) + sTermInfo[n - 1] * 60000)
    return date.fromordinal(date(1970,1,1).toordinal() + ms // 86400000)


if __name__ == '__main__':
    anchors = [
        (date(2026, 2, 17), (2026, 1, 1, False), '春节'),
        (date(2026, 2, 16), (2025, 12, 29, False), '除夕(腊月廿九)'),
        (date(2026, 3, 3), (2026, 1, 15, False), '元宵'),
        (date(2026, 10, 10), (2026, 9, 1, False), '九月初一'),
        (date(2026, 10, 14), (2026, 9, 5, False), '今天'),
        (date(2026, 10, 18), (2026, 9, 9, False), '重阳'),
        (date(2026, 10, 31), (2026, 9, 22, False), '九月廿二'),
        (date(2026, 11, 9), (2026, 10, 1, False), '十月初一'),
        (date(2020, 1, 25), (2020, 1, 1, False), '2020春节'),
        (date(2024, 2, 10), (2024, 1, 1, False), '2024春节'),
        (date(2010, 2, 14), (2010, 1, 1, False), '2010春节'),
        (date(2000, 1, 1), (1999, 11, 25, False), '2000元旦'),
        (date(2023, 1, 22), (2023, 1, 1, False), '2023春节'),
        (date(2033, 1, 31), (2033, 1, 1, False), '2033春节'),
    ]
    ok = 0
    for d, exp, name in anchors:
        got = solar_to_lunar2(d)
        flag = 'OK ' if got == exp else 'FAIL'
        if got == exp:
            ok += 1
        print(f"{flag} {name:16s} {fmt(d)}  expect={exp} got={got}")
    print(f"\n锚点通过 {ok}/{len(anchors)}")

    print("\n=== 2026 年节气抽样 ===")
    for n in [3, 4, 5, 7, 20, 24]:
        print(f"  {TERM_NAMES[n-1]}: {term_day(2026, n)}")
