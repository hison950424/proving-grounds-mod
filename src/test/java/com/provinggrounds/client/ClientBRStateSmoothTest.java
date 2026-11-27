package com.provinggrounds.client;

import com.provinggrounds.network.BRStatePacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 檔案: ClientBRStateSmoothTest.java
 * 目的: 驗證安全區邊界的客戶端線性平滑插值邏輯（初始對齊、幀間過渡、中斷重插與清除重設）。
 */
class ClientBRStateSmoothTest {

    private static BRStatePacket activePkt(float cx, float cz, float r) {
        return new BRStatePacket(
            ClientBRState.PHASE_ACTIVE, 10, true, (byte) 1, (byte) 4,
            cx, cz, r, 0f, 0f, 50f,
            (short) 2, (short) 2, ClientBRState.SELF_ALIVE, 1.0f,
            ClientBRState.PROGRESS_NONE, 0f, ClientBRState.TIMER_NONE, (short) 0, (byte) 0, List.of()
        );
    }

    @Test
    void smoothCircleInterpolatesBetweenUpdatesAndHandlesMidFrameArrival() {
        ClientBRState.clear();
        long t0 = 1_000_000_000L;
        ClientBRState.applyAt(activePkt(0f, 0f, 200f), t0);

        ClientBRState.SmoothCircle s0 = ClientBRState.smoothCircleAt(t0 + 50_000_000L);
        assertEquals(200.0, s0.radius(), 1e-6);

        // 經過 100ms 收到下一個縮圈座標
        long t1 = t0 + 100_000_000L;
        ClientBRState.applyAt(activePkt(10f, -6f, 190f), t1);

        // 在 t = 0、0.5、1.0 時平順移動過去
        ClientBRState.SmoothCircle a0 = ClientBRState.smoothCircleAt(t1);
        assertEquals(0.0, a0.cx(), 1e-6);
        assertEquals(200.0, a0.radius(), 1e-6);

        ClientBRState.SmoothCircle aMid = ClientBRState.smoothCircleAt(t1 + 50_000_000L);
        assertEquals(5.0, aMid.cx(), 1e-6);
        assertEquals(-3.0, aMid.cz(), 1e-6);
        assertEquals(195.0, aMid.radius(), 1e-6);

        ClientBRState.SmoothCircle aEnd = ClientBRState.smoothCircleAt(t1 + 100_000_000L);
        assertEquals(10.0, aEnd.cx(), 1e-6);
        assertEquals(-6.0, aEnd.cz(), 1e-6);
        assertEquals(190.0, aEnd.radius(), 1e-6);

        // 若還沒到達終點就提前收到再下一次更新，應從當下插值位置無縫接軌，不倒退回起點
        long t2 = t1 + 100_000_000L;
        ClientBRState.applyAt(activePkt(20f, -12f, 180f), t2);
        ClientBRState.applyAt(activePkt(30f, -18f, 170f), t2 + 50_000_000L);
        ClientBRState.SmoothCircle seamless = ClientBRState.smoothCircleAt(t2 + 50_000_000L);
        assertEquals(15.0, seamless.cx(), 1e-3);
        assertEquals(185.0, seamless.radius(), 1e-3);

        // 大距離瞬間切換應直接跳齊，不做超長距離慢速滑行
        ClientBRState.applyAt(activePkt(5000f, 5000f, 300f), t2 + 100_000_000L);
        ClientBRState.SmoothCircle snapped = ClientBRState.smoothCircleAt(t2 + 100_000_000L);
        assertEquals(5000.0, snapped.cx(), 1e-6);
        assertEquals(300.0, snapped.radius(), 1e-6);

        ClientBRState.clear();
    }
}
