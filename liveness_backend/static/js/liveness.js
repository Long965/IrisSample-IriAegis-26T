/**
 * Web Liveness State Machine - Port 1:1 từ LivenessLogic.java & FaceCaptureActivity_3.java
 */

const State = {
    IDLE: 'IDLE',
    WAITING_FOR_FACE: 'WAITING_FOR_FACE',
    BLINK_REQUESTED: 'BLINK_REQUESTED',
    HEAD_SHAKE_REQUESTED: 'HEAD_SHAKE_REQUESTED',
    FLASH_CHALLENGE: 'FLASH_CHALLENGE',
    COMPLETED: 'COMPLETED',
    FAILED: 'FAILED'
};

// Ngưỡng chuẩn từ LivenessLogic.java
const EYE_CLOSED_THRESHOLD = 0.16;
const EYE_OPEN_THRESHOLD = 0.22;
const HEAD_SHAKE_LEFT_ANGLE = -11.5;
const HEAD_SHAKE_RIGHT_ANGLE = 11.5;
const HEAD_CENTER_ANGLE = 10.0;

// Cấu hình ước lượng khoảng cách camera (cm)
const MAX_DISTANCE_CM = 30.0;
const REAL_IPD_CM = 6.3;        // Khoảng cách trung bình giữa 2 đồng tử (cm)
const FOCAL_LENGTH_NORM = 0.86; // Tiêu cự chuẩn hóa cho webcam phổ thông (~60° FOV)

const STATIC_EULER_THRESHOLD = 0.01;
const STATIC_NOSE_THRESHOLD = 0.0003; // Tọa độ chuẩn hóa [0..1] tương đương ~0.15px trên khung 640px
const MAX_STATIC_FRAMES_ALLOWED = 28;
const FREEZE_RESUME_STATIC_FRAMES = 18;

const MAX_FRAME_PITCH_ROLL_DELTA = 25.0;
const MAX_FRAME_YAW_DELTA_NORMAL = 35.0;
const MIN_3D_PERSPECTIVE_VARIANCE = 0.015;
const MIN_NOSE_PARALLAX_VARIANCE = 0.05;
const DEFAULT_TIMEOUT_MS = 25000;

let currentState = State.IDLE;
let timeoutTimer = null;

// Biến theo dõi chớp mắt
let initialOpenFrames = 0;
let closedFrameCount = 0;
let hasEyeClosed = false;
let blinkStartYaw = null, blinkStartPitch = null, blinkStartRoll = null;

// Biến theo dõi lắc đầu & 3D Nose-to-Eye Parallax
let hasTurnedLeft = false;
let hasTurnedRight = false;
let centerHoldFrames = 0;
let minEyeRatio = 1.0, maxEyeRatio = 0.0;
let minNoseRatio = 1.0, maxNoseRatio = 0.0;
let has3DPerspectiveShift = false;

// Biến chống đứng hình (Freeze) & cắt ghép (Splice/Cut)
let consecutiveStaticFrames = 0;
let prevYaw = null, prevPitch = null, prevRoll = null;
let prevNoseX = null, prevNoseY = null;
let prevEAR = null;
let latestFaceBBox = null;

const videoElement = document.getElementById('webcam');
const canvasElement = document.getElementById('canvas-hidden');
const instructionText = document.getElementById('instruction-text');
const faceOvalRing = document.getElementById('face-oval-ring');
const userSelect = document.getElementById('user-select');
const logsTableBody = document.getElementById('logs-table-body');

let usersList = [];

// ==================== KHỞI TẠO & API DATABASE ====================

async function fetchUsers() {
    try {
        const res = await fetch('/api/v1/users');
        const data = await res.json();
        if (data.success && data.users && data.users.length > 0) {
            usersList = data.users;
            userSelect.innerHTML = usersList.map(u => {
                const statusTag = u.has_photo ? '✅ Đã có ảnh' : '⚠️ Chưa có ảnh';
                return `<option value="${u.entity_id}" data-folder="${u.folder}">[${u.folder.toUpperCase()}] ${u.entity_id} - ${u.full_name} (${statusTag})</option>`;
            }).join('');
        } else {
            usersList = [];
            userSelect.innerHTML = `<option value="">-- Không tìm thấy dữ liệu trong IrisDatabase.db (${data.message || '0 bản ghi'}) --</option>`;
        }
    } catch (e) {
        console.error("Lỗi gọi /api/v1/users:", e);
        usersList = [];
        userSelect.innerHTML = `<option value="">-- Lỗi kết nối API /api/v1/users --</option>`;
    }
}

async function fetchLogs() {
    const res = await fetch('/api/v1/attendance-logs');
    const data = await res.json();
    if (data.success) {
        logsTableBody.innerHTML = data.logs.map(l => `
            <tr class="border-b border-slate-800/50">
                <td class="p-2">
                    <div class="font-semibold text-slate-200">${l.full_name}</div>
                    <div class="text-[10px] text-slate-500">${l.timestamp} | ${l.reason}</div>
                </td>
                <td class="p-2">
                    <span class="px-2 py-0.5 rounded-full text-[10px] ${l.liveness_status === 'PASSED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                        ${l.liveness_status}
                    </span>
                </td>
                <td class="p-2 text-right text-indigo-400">
                    ${(l.real_score || 0).toFixed(2)} / ${(l.shift_score || 0).toFixed(3)}
                </td>
            </tr>
        `).join('') || `<tr><td colspan="3" class="p-4 text-center text-slate-500">Chưa có dữ liệu</td></tr>`;
    }
}

function getSelectedEntity() {
    const opt = userSelect.options[userSelect.selectedIndex];
    return {
        entityId: opt ? opt.value : 'SV001',
        folder: opt ? opt.getAttribute('data-folder') : 'students'
    };
}

// ==================== TÍNH TOÁN HÌNH HỌC KHUÔN MẶT (3D EULER & LANDMARKS) ====================

function dist2D(p1, p2) {
    return Math.hypot(p1.x - p2.x, p1.y - p2.y);
}

function getFaceMetrics(landmarks) {
    // 1. Bounding Box chuẩn hóa [0..1]
    let minX = 1, minY = 1, maxX = 0, maxY = 0;
    for (const pt of landmarks) {
        if (pt.x < minX) minX = pt.x;
        if (pt.y < minY) minY = pt.y;
        if (pt.x > maxX) maxX = pt.x;
        if (pt.y > maxY) maxY = pt.y;
    }
    const bbox = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };

    // 2. EAR (Eye Aspect Ratio)
    const leftEAR = dist2D(landmarks[386], landmarks[374]) / (dist2D(landmarks[362], landmarks[263]) + 1e-6);
    const rightEAR = dist2D(landmarks[159], landmarks[145]) / (dist2D(landmarks[33], landmarks[133]) + 1e-6);
    const ear = (leftEAR + rightEAR) / 2.0;

    // 3. Góc Euler Yaw (Y), Pitch (X), Roll (Z)
    const nose = landmarks[1];
    const leftCheek = landmarks[234];
    const rightCheek = landmarks[454];
    const forehead = landmarks[10];
    const chin = landmarks[152];
    const leftEyeOuter = landmarks[33];
    const rightEyeOuter = landmarks[263];

    const dLeft = dist2D(nose, leftCheek);
    const dRight = dist2D(nose, rightCheek);
    // Do video CSS lật gương (-scale-x-100), đảo dấu để khớp hướng quay trái/phải của người dùng
    const yaw = -((dLeft - dRight) / (dLeft + dRight + 1e-6)) * 90.0;

    const dTop = dist2D(nose, forehead);
    const dBottom = dist2D(nose, chin);
    const pitch = ((dTop - dBottom) / (dTop + dBottom + 1e-6)) * 90.0;

    const roll = Math.atan2(rightEyeOuter.y - leftEyeOuter.y, rightEyeOuter.x - leftEyeOuter.x) * (180 / Math.PI);

    // 4. Ước lượng khoảng cách từ khuôn mặt đến camera (cm)
    let distanceCm = 0;
    const leftIris = landmarks[468];
    const rightIris = landmarks[473];
    if (leftIris && rightIris) {
        const ipdNorm = dist2D(leftIris, rightIris);
        // Bù góc quay đầu (yaw) để khi lắc đầu không bị báo sai khoảng cách
        const cosYaw = Math.max(0.5, Math.cos((yaw * Math.PI) / 180.0));
        const adjustedIpd = ipdNorm / cosYaw;
        distanceCm = (REAL_IPD_CM * FOCAL_LENGTH_NORM) / (adjustedIpd + 1e-6);
    }

    return { bbox, ear, yaw, pitch, roll, nose, leftEyeOuter, rightEyeOuter, distanceCm };
}

// Kiểm tra khuôn mặt nằm vừa vặn trong khung Oval (tương đương checkFaceInCircle trên Android)
function checkFaceInOval(bbox) {
    const cx = bbox.x + bbox.w / 2.0;
    const cy = bbox.y + bbox.h / 2.0;
    const isPerforming = (currentState !== State.IDLE && currentState !== State.WAITING_FOR_FACE);
    const tol = isPerforming ? 0.16 : 0.10;

    const centerOk = Math.abs(cx - 0.5) <= (0.22 + tol) && Math.abs(cy - 0.5) <= (0.28 + tol);
    const sizeOk = bbox.w >= 0.18 && bbox.w <= 0.65;
    return centerOk && sizeOk;
}

// ==================== STATE MACHINE (1:1 LivenessLogic.java) ====================

function onFaceMeshResults(results) {
    if (!results.multiFaceLandmarks || results.multiFaceLandmarks.length === 0) {
        latestFaceBBox = null;
        if (currentState !== State.IDLE && currentState !== State.COMPLETED && currentState !== State.FAILED) {
            faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 border-white";
            instructionText.innerText = "Không tìm thấy khuôn mặt, vui lòng nhìn thẳng camera";
        }
        return;
    }

    const landmarks = results.multiFaceLandmarks[0];
    const m = getFaceMetrics(landmarks);
    latestFaceBBox = m.bbox;

    if (currentState === State.IDLE || currentState === State.COMPLETED || currentState === State.FAILED || currentState === State.FLASH_CHALLENGE) {
        return;
    }

    // 0a. Kiểm tra khoảng cách khuôn mặt đến camera (> 30cm)
    if (m.distanceCm > MAX_DISTANCE_CM) {
        faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 invalid";
        instructionText.innerText = `Vui lòng đưa khuôn mặt lại gần camera hơn (~${Math.round(m.distanceCm)}cm > 30cm)`;
        return;
    }

    // 0b. Kiểm tra khuôn mặt nằm trong khung Oval
    if (!checkFaceInOval(m.bbox)) {
        faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 border-white";
        instructionText.innerText = "Vui lòng đặt khuôn mặt vừa vặn vào trong khung";
        return;
    } else {
        faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 valid";
    }

    // A. Kiểm tra đứng hình video (Freeze Frame Detection)
    if (prevYaw !== null && prevNoseX !== null) {
        const dY = Math.abs(m.yaw - prevYaw);
        const dX = Math.abs(m.pitch - prevPitch);
        const dZ = Math.abs(m.roll - prevRoll);
        const dNoseX = Math.abs(m.nose.x - prevNoseX);
        const dNoseY = Math.abs(m.nose.y - prevNoseY);
        const dEAR = Math.abs(m.ear - prevEAR);

        const isStatic = (dY <= STATIC_EULER_THRESHOLD) && (dX <= STATIC_EULER_THRESHOLD) &&
                         (dZ <= STATIC_EULER_THRESHOLD) && (dNoseX <= STATIC_NOSE_THRESHOLD) &&
                         (dNoseY <= STATIC_NOSE_THRESHOLD) && (dEAR <= 0.001);

        if (isStatic) {
            consecutiveStaticFrames++;
            if (consecutiveStaticFrames >= MAX_STATIC_FRAMES_ALLOWED) {
                failLiveness("Phát hiện gián đoạn chuyển động!");
                return;
            }
        } else {
            if (consecutiveStaticFrames >= FREEZE_RESUME_STATIC_FRAMES) {
                failLiveness("Phát hiện gián đoạn chuyển động!");
                return;
            }
            consecutiveStaticFrames = 0;

            // B. Kiểm tra cắt ghép video (Splice / Cut Detection)
            const allowedMaxYaw = (currentState === State.HEAD_SHAKE_REQUESTED) ? 50.0 : MAX_FRAME_YAW_DELTA_NORMAL;
            if (dY > allowedMaxYaw || dX > MAX_FRAME_PITCH_ROLL_DELTA || dZ > MAX_FRAME_PITCH_ROLL_DELTA) {
                failLiveness("Phát hiện chuỗi chuyển động bất thường!");
                return;
            }
        }
    }

    prevYaw = m.yaw;
    prevPitch = m.pitch;
    prevRoll = m.roll;
    prevNoseX = m.nose.x;
    prevNoseY = m.nose.y;
    prevEAR = m.ear;

    // C. Kiểm tra 3D Nose-to-Eye Parallax khi quay đầu
    if (currentState === State.HEAD_SHAKE_REQUESTED && m.bbox.w > 0) {
        const eyeDist = Math.abs(m.rightEyeOuter.x - m.leftEyeOuter.x);
        const eyeRatio = eyeDist / m.bbox.w;
        if (eyeRatio < minEyeRatio) minEyeRatio = eyeRatio;
        if (eyeRatio > maxEyeRatio) maxEyeRatio = eyeRatio;

        let noseVar = 0;
        if (eyeDist > 0.02) {
            const minEyeX = Math.min(m.leftEyeOuter.x, m.rightEyeOuter.x);
            const noseRatio = (m.nose.x - minEyeX) / eyeDist;
            if (noseRatio < minNoseRatio) minNoseRatio = noseRatio;
            if (noseRatio > maxNoseRatio) maxNoseRatio = noseRatio;
            noseVar = maxNoseRatio - minNoseRatio;
        }

        if ((maxEyeRatio - minEyeRatio) >= MIN_3D_PERSPECTIVE_VARIANCE || noseVar >= MIN_NOSE_PARALLAX_VARIANCE) {
            has3DPerspectiveShift = true;
        }
    }

    // D. Chạy luồng State Machine
    if (currentState === State.WAITING_FOR_FACE) {
        if (Math.abs(m.yaw) <= 12.0) {
            updateStepUI('step-1', 'completed', 'Đã định vị');
            updateStepUI('step-2', 'active', 'Đang chờ...');
            changeState(State.BLINK_REQUESTED, "Bước 1/3: Vui lòng giữ thẳng đầu và chớp mắt");
        } else {
            instructionText.innerText = "Vui lòng nhìn thẳng vào camera để bắt đầu";
        }
    } else if (currentState === State.BLINK_REQUESTED) {
        checkBlink(m);
    } else if (currentState === State.HEAD_SHAKE_REQUESTED) {
        checkHeadShake(m);
    }
}

function checkBlink(m) {
    if (Math.abs(m.yaw) > 15.0) {
        initialOpenFrames = 0;
        closedFrameCount = 0;
        hasEyeClosed = false;
        instructionText.innerText = "Bước 1/3: Giữ thẳng đầu và chớp mắt";
        return;
    }

    const eyesClosed = m.ear <= EYE_CLOSED_THRESHOLD;
    const eyesOpen = m.ear >= EYE_OPEN_THRESHOLD;

    if (!hasEyeClosed) {
        if (eyesOpen) initialOpenFrames++;
        if (initialOpenFrames >= 3 && eyesClosed) {
            blinkStartYaw = m.yaw;
            blinkStartPitch = m.pitch;
            blinkStartRoll = m.roll;
            hasEyeClosed = true;
            closedFrameCount = 1;
        }
    } else {
        if (eyesClosed) {
            closedFrameCount++;
            if (closedFrameCount > 15) {
                initialOpenFrames = 0;
                closedFrameCount = 0;
                hasEyeClosed = false;
            }
        } else if (eyesOpen) {
            if (Math.abs(m.yaw - blinkStartYaw) > 15.0 ||
                Math.abs(m.pitch - blinkStartPitch) > 15.0 ||
                Math.abs(m.roll - blinkStartRoll) > 15.0) {
                failLiveness("Phát hiện chuỗi chuyển động bất thường!");
                return;
            }
            updateStepUI('step-2', 'completed', 'Đã chớp mắt');
            updateStepUI('step-3', 'active', 'Đang chờ...');
            changeState(State.HEAD_SHAKE_REQUESTED, "Bước 2/3: Vui lòng quay sang trái & quay sang phải");
        }
    }
}

function checkHeadShake(m) {
    if (m.yaw <= HEAD_SHAKE_LEFT_ANGLE) hasTurnedLeft = true;
    if (m.yaw >= HEAD_SHAKE_RIGHT_ANGLE) hasTurnedRight = true;

    if (hasTurnedLeft && hasTurnedRight) {
        if (!has3DPerspectiveShift) {
            instructionText.innerText = "⚠️ Hãy lắc đầu thật 3D sang trái và phải";
            return;
        }
        if (Math.abs(m.yaw) > HEAD_CENTER_ANGLE) {
            centerHoldFrames = 0;
            instructionText.innerText = "Bước 2/3: Tốt! Giờ hãy nhìn thẳng vào camera";
            return;
        }
        centerHoldFrames++;
        if (centerHoldFrames >= 3) {
            updateStepUI('step-3', 'completed', 'Đạt 3D Parallax');
            updateStepUI('step-4', 'active', 'Đang kiểm tra...');
            triggerEdgeFlashChallenge();
        }
    }
}

// ==================== EDGE FLASH CHALLENGE (KHÔNG CHE MẶT) + GỌI FASTAPI ====================

async function triggerEdgeFlashChallenge() {
    changeState(State.FLASH_CHALLENGE, "Bước 3/3: Giữ thẳng mặt...");
    const bboxSnapshot = latestFaceBBox || { x: 0.3, y: 0.2, w: 0.4, h: 0.5 };

    // 1. Thu mẫu Baseline (150ms)
    await new Promise(r => setTimeout(r, 150));
    const baselineBlob = await captureWebcamBlob();

    // 2. Nháy viền ĐỎ (#FF0000), giữa mặt trong suốt
    faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 flashing-red";
    await new Promise(r => setTimeout(r, 350));
    const redBlob = await captureWebcamBlob();

    // 3. Nháy viền XANH DƯƠNG (#0000FF), giữa mặt trong suốt
    faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 flashing-blue";
    await new Promise(r => setTimeout(r, 350));
    const blueBlob = await captureWebcamBlob();

    // Trả về màu mặc định
    faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 valid";

    // 4. Gửi lên FastAPI để chấm điểm Flash + MiniVision AI
    const { entityId, folder } = getSelectedEntity();
    const formData = new FormData();
    formData.append("entity_id", entityId);
    formData.append("folder", folder);
    formData.append("bbox_x", bboxSnapshot.x);
    formData.append("bbox_y", bboxSnapshot.y);
    formData.append("bbox_w", bboxSnapshot.w);
    formData.append("bbox_h", bboxSnapshot.h);
    formData.append("baseline_file", baselineBlob, "base.jpg");
    formData.append("red_flash_file", redBlob, "red.jpg");
    formData.append("blue_flash_file", blueBlob, "blue.jpg");

    try {
        const res = await fetch("/api/v1/verify-liveness", { method: "POST", body: formData });
        const data = await res.json();
        clearTimeout(timeoutTimer);

        if (data.success) {
            changeState(State.COMPLETED, data.message);
            updateStepUI('step-4', 'completed', `Real: ${data.metrics.real_score}`);
            faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 valid";
        } else {
            changeState(State.FAILED, data.message);
            updateStepUI('step-4', 'failed', 'Từ chối');
            faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 invalid";
        }
        fetchLogs();
    } catch (e) {
        failLiveness("Lỗi kết nối server FastAPI");
    }
}

async function captureRegisterMode() {
    if (!latestFaceBBox) {
        const msg = "❌ Không tìm thấy khuôn mặt! Vui lòng đưa mặt vào khung hình trước khi chụp.";
        instructionText.innerText = msg;
        alert(msg);
        return;
    }

    const { entityId, folder } = getSelectedEntity();
    const bboxSnapshot = latestFaceBBox;

    instructionText.innerText = "⏳ Đang chụp ảnh đăng ký và đối soát khuôn mặt...";

    try {
        const blob = await captureWebcamBlob();
        const formData = new FormData();
        formData.append("entity_id", entityId);
        formData.append("folder", folder);
        formData.append("bbox_x", bboxSnapshot.x);
        formData.append("bbox_y", bboxSnapshot.y);
        formData.append("bbox_w", bboxSnapshot.w);
        formData.append("bbox_h", bboxSnapshot.h);
        formData.append("face_image", blob, "avatar.jpg");

        const res = await fetch("/api/v1/register-face", { method: "POST", body: formData });
        let data;
        const text = await res.text();
        try {
            data = JSON.parse(text);
        } catch (jErr) {
            data = { success: false, message: `❌ Lỗi Server (${res.status}): ${text || 'Internal Server Error'}` };
        }

        instructionText.innerText = data.message;
        alert(data.message);

        if (data.success) {
            await fetchUsers();
            if (entityId) userSelect.value = entityId;
        }
    } catch (e) {
        alert("❌ Lỗi chụp đăng ký: " + e.message);
        instructionText.innerText = "❌ Lỗi chụp đăng ký: " + e.message;
    }
}

async function uploadStudentPhotoFile(inputEl) {
    if (!inputEl.files || inputEl.files.length === 0) return;

    const file = inputEl.files[0];
    const { entityId, folder } = getSelectedEntity();

    const formData = new FormData();
    formData.append("entity_id", entityId);
    formData.append("folder", folder);
    formData.append("photo_file", file);

    instructionText.innerText = "⏳ Đang tải ảnh lên và kiểm tra trùng khuôn mặt...";

    try {
        const res = await fetch("/api/v1/upload-student-photo", { method: "POST", body: formData });
        let data;
        const text = await res.text();
        try {
            data = JSON.parse(text);
        } catch (jErr) {
            data = { success: false, message: `❌ Lỗi Server (${res.status}): ${text || 'Internal Server Error'}` };
        }

        instructionText.innerText = data.message;
        alert(data.message);

        if (data.success) {
            await fetchUsers();
            if (entityId) userSelect.value = entityId;
        }
    } catch (e) {
        alert("❌ Lỗi tải ảnh lên server: " + e.message);
    } finally {
        inputEl.value = "";
    }
}

function captureWebcamBlob() {
    return new Promise((resolve) => {
        const w = videoElement.videoWidth || 640;
        const h = videoElement.videoHeight || 480;
        canvasElement.width = w;
        canvasElement.height = h;
        const ctx = canvasElement.getContext('2d');

        // Lật gương ngang Canvas khớp 100% với màn hình xem trước (-scale-x-100) và camera selfie Android
        ctx.save();
        ctx.translate(w, 0);
        ctx.scale(-1, 1);
        ctx.drawImage(videoElement, 0, 0, w, h);
        ctx.restore();

        canvasElement.toBlob((blob) => resolve(blob), 'image/jpeg', 0.92);
    });
}

// ==================== ĐIỀU KHIỂN TRẠNG THÁI ====================

function startCheckinMode() {
    const { entityId, folder } = getSelectedEntity();
    const selectedUser = usersList.find(u => u.entity_id === entityId && u.folder === folder);

    // Chặn nếu chưa chọn đối tượng hoặc đối tượng chưa có ảnh mẫu
    if (!selectedUser) {
        const msg = "❌ Vui lòng chọn đối tượng trước khi xác thực!";
        resetLivenessState();
        instructionText.innerText = msg;
        alert(msg);
        return;
    }
    if (!selectedUser.has_photo) {
        const msg = `⚠️ ${selectedUser.full_name} (${entityId}) chưa có ảnh mẫu! Vui lòng chụp hoặc tải ảnh đăng ký trước khi check-in Liveness.`;
        resetLivenessState();
        instructionText.innerText = msg;
        alert(msg);
        return;
    }

    resetLivenessState();
    changeState(State.WAITING_FOR_FACE, "Vui lòng đưa khuôn mặt vào khung hình");
    updateStepUI('step-1', 'active', 'Đang định vị...');

    timeoutTimer = setTimeout(() => {
        if (currentState !== State.COMPLETED && currentState !== State.FAILED) {
            failLiveness("Hết thời gian xác thực (25s)");
        }
    }, DEFAULT_TIMEOUT_MS);
}

async function failLiveness(reason) {
    clearTimeout(timeoutTimer);
    changeState(State.FAILED, `❌ ${reason}`);
    faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 invalid";

    const { entityId, folder } = getSelectedEntity();
    const formData = new FormData();
    formData.append("entity_id", entityId);
    formData.append("folder", folder);
    formData.append("reason", reason);
    await fetch("/api/v1/log-client-fail", { method: "POST", body: formData });
    fetchLogs();
}

function resetLivenessState() {
    clearTimeout(timeoutTimer);
    currentState = State.IDLE;
    initialOpenFrames = 0;
    closedFrameCount = 0;
    hasEyeClosed = false;
    hasTurnedLeft = false;
    hasTurnedRight = false;
    centerHoldFrames = 0;
    minEyeRatio = 1.0; maxEyeRatio = 0.0;
    minNoseRatio = 1.0; maxNoseRatio = 0.0;
    has3DPerspectiveShift = false;
    consecutiveStaticFrames = 0;
    prevYaw = null; prevPitch = null; prevRoll = null;
    prevNoseX = null; prevNoseY = null; prevEAR = null;

    faceOvalRing.className = "w-56 h-72 rounded-[50%] border-4 border-white";
    ['step-1', 'step-2', 'step-3', 'step-4'].forEach(id => updateStepUI(id, '', 'Chờ...'));
    instructionText.innerText = "Chọn đối tượng và nhấn Xác thực Liveness (Check-in)";
}

function changeState(newState, msg) {
    currentState = newState;
    instructionText.innerText = msg;
}

function updateStepUI(stepId, stateClass, statusText) {
    const el = document.getElementById(stepId);
    if (!el) return;
    el.className = `step-item p-2.5 rounded-2xl border text-xs font-medium flex flex-col ${stateClass}`;
    el.querySelector('.step-status').innerText = statusText;
}

window.addEventListener('DOMContentLoaded', async () => {
    await fetchUsers();
    await fetchLogs();

    const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480, facingMode: "user" } });
    videoElement.srcObject = stream;

    const faceMesh = new FaceMesh({ locateFile: f => `https://cdn.jsdelivr.net/npm/@mediapipe/face_mesh/${f}` });
    faceMesh.setOptions({ maxNumFaces: 1, refineLandmarks: true, minDetectionConfidence: 0.5, minTrackingConfidence: 0.5 });
    faceMesh.onResults(onFaceMeshResults);

    const camera = new Camera(videoElement, {
        onFrame: async () => { await faceMesh.send({ image: videoElement }); },
        width: 640, height: 480
    });
    await camera.start();
});
