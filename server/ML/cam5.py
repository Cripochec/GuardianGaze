import cv2
import dlib
import time
import numpy as np

class FatigueDetector:
    def __init__(self, predictor_path="shape_predictor_68_face_landmarks.dat", camera_id=1):
        self.detector = dlib.get_frontal_face_detector()
        self.predictor = dlib.shape_predictor(predictor_path)
        self.cap = cv2.VideoCapture(camera_id)
        self.frame_width = 640
        self.frame_height = 480
        self.heatmap = np.zeros((self.frame_height, self.frame_width), dtype=np.float32)
        self.gaze_points = []
        self.blink_start_time = None
        self.blinks = []
        self.long_blinks = 0
        self.last_fatigue_time = 0
        self.fatigue_cooldown = 10  # секунд между предупреждениями

    def calculate_ear(self, eye):
        A = np.linalg.norm(np.array([eye[1].x, eye[1].y]) - np.array([eye[5].x, eye[5].y]))
        B = np.linalg.norm(np.array([eye[2].x, eye[2].y]) - np.array([eye[4].x, eye[4].y]))
        C = np.linalg.norm(np.array([eye[0].x, eye[0].y]) - np.array([eye[3].x, eye[3].y]))
        ear = (A + B) / (2.0 * C)
        return ear

    def get_pupil_position(self, eye_region, threshold=30):
        gray_eye = cv2.cvtColor(eye_region, cv2.COLOR_BGR2GRAY)
        _, threshold_eye = cv2.threshold(gray_eye, threshold, 255, cv2.THRESH_BINARY_INV)
        contours, _ = cv2.findContours(threshold_eye, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if contours:
            largest_contour = max(contours, key=cv2.contourArea)
            M = cv2.moments(largest_contour)
            if M["m00"] != 0:
                cx = int(M["m10"] / M["m00"])
                cy = int(M["m01"] / M["m00"])
                return (cx, cy)
        return None

    def classify_fatigue(self, blink_history, long_blinks, gaze_history):
        # Усталость: более 3 долгих морганий за 30 секунд или часто смотрит в сторону
        now = time.time()
        recent_blinks = [b for b in blink_history if now - b < 30]
        if long_blinks >= 3 or len(recent_blinks) > 15:
            return True
        # Если взгляд часто влево/вправо за последние 10 секунд
        directions = [g[1] for g in gaze_history if now - g[0] < 10]
        if directions.count("LEFT") > 5 or directions.count("RIGHT") > 5:
            return True
        return False

    def run(self):
        EAR_THRESHOLD = 0.2
        BLINK_DURATION = 0.2
        gaze_history = []

        while self.cap.isOpened():
            ret, frame = self.cap.read()
            if not ret:
                break

            frame = cv2.resize(frame, (self.frame_width, self.frame_height))
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = self.detector(gray)

            for face in faces:
                landmarks = self.predictor(gray, face)
                left_eye = [landmarks.part(i) for i in range(36, 42)]
                right_eye = [landmarks.part(i) for i in range(42, 48)]

                # EAR (blink detection)
                left_ear = self.calculate_ear(left_eye)
                right_ear = self.calculate_ear(right_eye)
                ear = (left_ear + right_ear) / 2.0

                # Blink logic
                if ear < EAR_THRESHOLD:
                    if self.blink_start_time is None:
                        self.blink_start_time = time.time()
                else:
                    if self.blink_start_time is not None:
                        blink_duration = time.time() - self.blink_start_time
                        self.blinks.append(time.time())
                        if blink_duration > BLINK_DURATION:
                            self.long_blinks += 1
                        self.blink_start_time = None

                # Gaze direction
                left_eye_pts = left_eye
                right_eye_pts = right_eye
                left_x1, left_y1 = min([p.x for p in left_eye_pts]), min([p.y for p in left_eye_pts])
                left_x2, left_y2 = max([p.x for p in left_eye_pts]), max([p.y for p in left_eye_pts])
                right_x1, right_y1 = min([p.x for p in right_eye_pts]), min([p.y for p in right_eye_pts])
                right_x2, right_y2 = max([p.x for p in right_eye_pts]), max([p.y for p in right_eye_pts])

                left_eye_region = frame[left_y1:left_y2, left_x1:left_x2]
                right_eye_region = frame[right_y1:right_y2, right_x1:right_x2]

                left_pupil = self.get_pupil_position(left_eye_region)
                right_pupil = self.get_pupil_position(right_eye_region)

                direction = "CENTER"
                if left_pupil and right_pupil:
                    left_ratio = left_pupil[0] / (left_x2 - left_x1 + 1e-6)
                    right_ratio = right_pupil[0] / (right_x2 - right_x1 + 1e-6)
                    gaze_ratio = (left_ratio + right_ratio) / 2
                    if gaze_ratio < 0.4:
                        direction = "LEFT"
                    elif gaze_ratio > 0.6:
                        direction = "RIGHT"
                    else:
                        direction = "CENTER"
                    gaze_history.append((time.time(), direction))

                    # Gaze map
                    left_global = (left_x1 + left_pupil[0], left_y1 + left_pupil[1])
                    right_global = (right_x1 + right_pupil[0], right_y1 + right_pupil[1])
                    gaze_point = (
                        int((left_global[0] + right_global[0]) / 2),
                        int((left_global[1] + right_global[1]) / 2)
                    )
                    self.gaze_points.append(gaze_point)
                    cv2.circle(self.heatmap, gaze_point, 8, 1, -1)

                # Визуализация
                for i in range(1, len(self.gaze_points)):
                    cv2.line(frame, self.gaze_points[i - 1], self.gaze_points[i], (0, 255, 255), 1)

                heatmap_norm = cv2.normalize(self.heatmap, None, 0, 255, cv2.NORM_MINMAX)
                heatmap_color = cv2.applyColorMap(heatmap_norm.astype(np.uint8), cv2.COLORMAP_JET)
                overlay = cv2.addWeighted(frame, 0.7, heatmap_color, 0.3, 0)
                cv2.imshow("Fatigue Detector", overlay)

                # Классификация усталости
                if self.classify_fatigue(self.blinks, self.long_blinks, gaze_history):
                    if time.time() - self.last_fatigue_time > self.fatigue_cooldown:
                        print("ВНИМАНИЕ: Обнаружены признаки усталости водителя!")
                        self.last_fatigue_time = time.time()
                        self.long_blinks = 0  # сбросить счетчик после предупреждения

            if cv2.waitKey(1) & 0xFF == ord('q'):
                break

        self.cap.release()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    detector = FatigueDetector()
    detector.run()