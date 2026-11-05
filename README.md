# FUTO Gesture Studio (`futo-org/android-keyboard` Adaptive Gesture Fork)

A privacy-first, 100% offline Android Gesture Typing Engine, Interactive Calibration Studio, and System Input Method (`InputMethodService`) built upon the architecture of [FUTO Keyboard (`futo-org/android-keyboard`)](https://github.com/futo-org/android-keyboard).

This project enhances standard gesture typing recognition with **Curvature-Velocity Inflection Detection**, **Band-Constrained Dynamic Time Warping (DTW)**, and a continuous **On-Device User Writing Style Learning Engine** backed by Room SQLite.

---

## 📦 Prebuilt APK in Repository

- **Root APK Path:** `futo-gesture-studio-debug.apk`
- **Standard Gradle Output:** `app/build/outputs/apk/debug/app-debug.apk`

You can also export the project or generate signed APKs/AABs directly from the AI Studio Settings menu.

---

## 🚀 Key Improvements Over Baseline Gesture Typing

### 1. Multi-Stage Gesture Recognition Pipeline (`GestureTypingEngine.kt`)
- **Savitzky-Golay 3-Tap Trajectory Smoothing:** Eliminates pointer micro-jitter at touch-down and lift-off while preserving sharp interior vertices.
- **32-Point Equidistant & Kinematic Resampling:** Normalizes arbitrary swipe speeds into a 32-node spatial curve annotated with instantaneous velocity $v_i$ and turning angle $\Delta\theta_i$.
- **Corner Inflection & Double-Letter Loop Detection:**
  - Automatically identifies sharp directional changes ($\Delta\theta_i \ge 31^\circ$) as mandatory interior key anchors.
  - Detects low-velocity dwell zones and micro-loops ($v_i < 0.36\,\bar{v}$) to accurately disambiguate words containing consecutive duplicate letters (e.g., *hello* vs. *help*, *coffee* vs. *code*, *feel* vs. *fell*, *too* vs. *to*).
- **Sakoe-Chiba Band-Constrained Dynamic Time Warping (DTW):** Evaluates non-linear path alignment between the user's swipe trajectory and dynamically shifted 32-point polygonal word templates.

### 2. Continuous On-Device Writing Style Learning (`WritingStyleLearner.kt`)
All personal adaptation stays strictly on-device in `futo_adaptive_gesture.db` (Room SQLite):
1. **Per-Key Spatial Hitbox Drift ($\Delta x_k, \Delta y_k$ & $\sigma_k$):**
   - Aligns confirmed gesture anchors against canonical QWERTY key coordinates and updates an Exponential Moving Average (EMA) centroid offset for each of the 26 keys (`A`–`Z`).
   - Adapts to right-thumb or left-thumb ergonomic arcs and corner-rounding habits.
2. **Supervised Candidate Chip Correction Learning:**
   - Selecting a secondary candidate chip in the suggestion bar replaces the last committed word and triggers a boosted learning rate update ($1.45\times \eta$) across spatial key offsets, word frequency, bigrams, and gesture prototypes.
3. **Contextual N-Gram (Bigram) Language Model ($P(w_i \mid w_{i-1})$):**
   - Learns personal word-pair transitions to automatically disambiguate overlapping swipe paths based on preceding word context (e.g., *"came **from**"* vs. *"new **form**"*, *"at **night**"* vs. *"it **might**"*).
4. **16-Point Personal Gesture Prototypes:**
   - Stores compressed 16-point normalized swipe trajectories for words you swipe and confirm, blending personal prototype DTW similarity directly into future candidate scoring.

---

## 📱 Application & System IME Architecture

- **`FutoAdaptiveInputMethodService` (`com.example.ime`):**
  - Full Android `InputMethodService` registered in `AndroidManifest.xml` (`res/xml/method.xml`). Can be enabled in Android System Settings (*System → Keyboard → On-screen keyboard*) to type across any app.
- **4-Tab Interactive Studio (`MainActivity.kt`):**
  1. **Sandbox:** Live gesture typing buffer, interactive calibration phrase drills with word-by-word accuracy highlighting, **Simulate Thumb Swipe** automation, and the docked FUTO Adaptive Keyboard with a live **DRIFT** HUD toggle.
  2. **Decoder Lab:** Visualizes the 32-point resampled path, start/end/corner/loop inflection nodes, and a side-by-side breakdown of **Static Baseline vs. User-Adapted** scores.
  3. **Style Profile:** Interactive **26-Key Spatial Hitbox Drift Heatmap**, one-tap **Right-Thumb / Left-Thumb Arc** calibration presets, and real-time sliders for decoder hyperparameters ($\eta$ learning rate, DTW shape weight, spatial anchor weight, corner inflection weight).
  4. **Lexicon & N-Grams:** Add custom words or bigram transitions, inspect learned 16-point personal swipe prototype sparklines, and manage personal vocabulary weights.

---

## 🛠️ Building & Running Tests

```bash
# Assemble debug APK
gradle assembleDebug

# Run local JVM unit tests for GestureTypingEngine & WritingStyleLearner
gradle :app:testDebugUnitTest
```
