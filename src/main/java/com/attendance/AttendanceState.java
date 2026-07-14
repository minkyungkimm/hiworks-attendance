package com.attendance;

import java.util.concurrent.atomic.AtomicReference;

public class AttendanceState {

    public enum Decision {
        PENDING,    // 아직 응답 없음 → 기본값으로 8시 실행
        CHECKIN_8,  // 텔레그램에서 "8시 출근" 선택
        CHECKIN_9,  // 텔레그램에서 "9시 출근" 선택
        VACATION    // 텔레그램에서 "연차" 선택 → 출퇴근 체크 전체 건너뜀
    }

    private static final AtomicReference<Decision> decision = new AtomicReference<>(Decision.PENDING);

    public static void reset() {
        decision.set(Decision.PENDING);
    }

    public static void set(Decision d) {
        decision.set(d);
    }

    public static Decision get() {
        return decision.get();
    }
}
