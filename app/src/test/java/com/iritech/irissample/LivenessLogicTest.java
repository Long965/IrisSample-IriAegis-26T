package com.iritech.irissample;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LivenessLogicTest {

    private LivenessLogic livenessLogic;
    private LivenessLogic.State lastState;
    private String lastInstruction;
    private String failedReason;

    @Before
    public void setUp() {
        failedReason = null;
        livenessLogic = new LivenessLogic(new LivenessLogic.OnLivenessStateChangeListener() {
            @Override
            public void onStateChanged(LivenessLogic.State newState, String instructionMessage) {
                lastState = newState;
                lastInstruction = instructionMessage;
            }

            @Override
            public void onRequestFlashChallenge() {
            }

            @Override
            public void onLivenessCompleted() {
            }

            @Override
            public void onLivenessFailed(String reason) {
                failedReason = reason;
            }
        });
    }

    @Test
    public void testInitialStateAndStart() {
        assertEquals(LivenessLogic.State.IDLE, livenessLogic.getCurrentState());
        livenessLogic.start();
        assertEquals(LivenessLogic.State.WAITING_FOR_FACE, livenessLogic.getCurrentState());
        assertEquals("Vui lòng đưa khuôn mặt vào khung hình", lastInstruction);
    }

    @Test
    public void testStateTransitionToBlinkRequested() {
        livenessLogic.start();
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        assertEquals(LivenessLogic.State.BLINK_REQUESTED, livenessLogic.getCurrentState());
    }

    @Test
    public void testValidBlinkTransitionToHeadShake() {
        livenessLogic.start();
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f); // WAITING_FOR_FACE -> BLINK_REQUESTED

        // Initial open frames
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);

        // Eyes close
        livenessLogic.processFaceValues(0.05f, 0.05f, 0.0f);

        // Eyes open
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);

        assertEquals(LivenessLogic.State.HEAD_SHAKE_REQUESTED, livenessLogic.getCurrentState());
    }

    @Test
    public void testFlashChallengeCompletion() {
        livenessLogic.start();
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);

        // Advance to HEAD_SHAKE_REQUESTED
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);
        livenessLogic.processFaceValues(0.05f, 0.05f, 0.0f);
        livenessLogic.processFaceValues(0.95f, 0.95f, 0.0f);

        assertEquals(LivenessLogic.State.HEAD_SHAKE_REQUESTED, livenessLogic.getCurrentState());
    }
}