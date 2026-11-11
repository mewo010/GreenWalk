import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  TrafficLightId,
  MeasurementType,
  MeasurementEvent,
  WalkingSettings,
  ManualConfig
} from './types';
import {
  analyzeLight,
  analyzeSync,
  calculateDeparturePlan,
  formatEpochTime,
  formatSecondsToMinutes
} from './engine';
import {
  Clock,
  Navigation,
  Activity,
  History,
  RotateCcw,
  Footprints,
  Sliders,
  ShieldCheck,
  Trash2,
  Plus,
  Minus,
  CheckCircle2,
  AlertTriangle,
  Zap
} from 'lucide-react';

export default function App() {
  const [currentTimeMs, setCurrentTimeMs] = useState<number>(Date.now());
  const [measurements, setMeasurements] = useState<MeasurementEvent[]>(() => {
    try {
      const saved = localStorage.getItem('greenwalk_measurements');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [walkingSettings, setWalkingSettings] = useState<WalkingSettings>(() => {
    try {
      const saved = localStorage.getItem('greenwalk_walking');
      return saved ? JSON.parse(saved) : { walkToLight1Seconds: 60, walkBetweenLightsSeconds: 90, safetyBufferSeconds: 10 };
    } catch {
      return { walkToLight1Seconds: 60, walkBetweenLightsSeconds: 90, safetyBufferSeconds: 10 };
    }
  });

  const [manualConfig, setManualConfig] = useState<ManualConfig>(() => {
    try {
      const saved = localStorage.getItem('greenwalk_manual');
      return saved ? JSON.parse(saved) : {
        isEnabled: false,
        light1CycleSeconds: 75,
        light1GreenDurationSeconds: 22,
        light1OffsetSeconds: 0,
        light2CycleSeconds: 75,
        light2GreenDurationSeconds: 22,
        light2OffsetSeconds: 30
      };
    } catch {
      return {
        isEnabled: false,
        light1CycleSeconds: 75,
        light1GreenDurationSeconds: 22,
        light1OffsetSeconds: 0,
        light2CycleSeconds: 75,
        light2GreenDurationSeconds: 22,
        light2OffsetSeconds: 30
      };
    }
  });

  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [showHistory, setShowHistory] = useState<boolean>(false);
  const [lastTapTimes, setLastTapTimes] = useState<Record<string, number>>({});

  // Clock ticker every 500ms
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTimeMs(Date.now());
    }, 500);
    return () => clearInterval(timer);
  }, []);

  // Save changes to localStorage
  useEffect(() => {
    localStorage.setItem('greenwalk_measurements', JSON.stringify(measurements));
  }, [measurements]);

  useEffect(() => {
    localStorage.setItem('greenwalk_walking', JSON.stringify(walkingSettings));
  }, [walkingSettings]);

  useEffect(() => {
    localStorage.setItem('greenwalk_manual', JSON.stringify(manualConfig));
  }, [manualConfig]);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((prev) => (prev === msg ? null : prev));
    }, 3200);
  }, []);

  const triggerVibrate = (ms: number = 60) => {
    if (typeof window !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(ms);
      } catch {
        // Safe fallback
      }
    }
  };

  const handleRecordMeasurement = (lightId: TrafficLightId, type: MeasurementType = 'GREEN_START') => {
    const now = Date.now();
    const key = `${lightId}_${type}`;
    const lastTap = lastTapTimes[key] || 0;

    if (now - lastTap < 2500) {
      showToast('לחיצה נקלטה כבר, אנא המתן בין מדידות');
      return;
    }

    setLastTapTimes((prev) => ({ ...prev, [key]: now }));
    triggerVibrate(80);

    const newEvent: MeasurementEvent = {
      id: Math.random().toString(36).substring(2, 9),
      lightId,
      timestamp: now,
      type
    };

    setMeasurements((prev) => [...prev, newEvent]);
    const lightTitle = lightId === 'LIGHT_1' ? 'רמזור 1' : 'רמזור 2';
    const actionTitle = type === 'GREEN_START' ? 'הפך לירוק' : 'הפך לאדום';
    showToast(`נרשם: ${lightTitle} ${actionTitle} (${formatEpochTime(now)})`);
  };

  const handleDeleteMeasurement = (id: string) => {
    setMeasurements((prev) => prev.filter((m) => m.id !== id));
    showToast('מדידה נמחקה');
  };

  const handleClearAll = () => {
    if (window.confirm('האם אתה בטוח שברצונך לאפס את כל המדידות?')) {
      setMeasurements([]);
      showToast('כל המדידות אופסו');
    }
  };

  // Calculations
  const light1Analysis = useMemo(
    () => analyzeLight(measurements, 'LIGHT_1', currentTimeMs, manualConfig),
    [measurements, currentTimeMs, manualConfig]
  );

  const light2Analysis = useMemo(
    () => analyzeLight(measurements, 'LIGHT_2', currentTimeMs, manualConfig),
    [measurements, currentTimeMs, manualConfig]
  );

  const syncAnalysis = useMemo(
    () => analyzeSync(light1Analysis, light2Analysis, walkingSettings),
    [light1Analysis, light2Analysis, walkingSettings]
  );

  const departurePlan = useMemo(
    () => calculateDeparturePlan(light1Analysis, light2Analysis, walkingSettings, currentTimeMs),
    [light1Analysis, light2Analysis, walkingSettings, currentTimeMs]
  );

  return (
    <div className="min-h-screen bg-[#090C10] text-[#F1F5F9] pb-16 px-4 pt-4 max-w-lg mx-auto">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-[#1C2433] text-[#00E676] px-5 py-2.5 rounded-full shadow-2xl border border-[#00E676]/40 text-sm font-semibold flex items-center gap-2 animate-bounce">
          <CheckCircle2 className="w-4 h-4 text-[#00E676]" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Header */}
      <header className="flex items-center justify-between py-2 mb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-gradient-to-br from-[#00E676]/20 to-[#131822] border border-[#00E676]/30 flex items-center justify-center font-black text-[#00E676] text-sm shadow-md">
            GW
          </div>
          <div>
            <h1 className="text-xl font-black tracking-wide text-white leading-tight">GreenWalk</h1>
            <p className="text-xs text-[#94A3B8]">תזמון רמזורים להולכי רגל</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowHistory(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#131822] border border-[#2D3B52] text-xs font-semibold text-[#38BDF8] hover:bg-[#1C2433] transition"
          >
            <History className="w-3.5 h-3.5" />
            <span>היסטוריה ({measurements.length})</span>
          </button>
          <button
            onClick={handleClearAll}
            title="איפוס מדידות"
            className="w-8 h-8 rounded-full bg-[#131822] border border-[#2D3B52] flex items-center justify-center text-[#94A3B8] hover:text-[#FF385C] transition"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* 1. Live Digital Clock */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-lg flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="w-2.5 h-2.5 rounded-full bg-[#00E676] animate-pulse"></span>
            <span className="text-xs text-[#94A3B8] font-medium">שעון מערכת בזמן אמת</span>
          </div>
          <div className="text-3xl font-bold font-mono-numbers tracking-widest text-white">
            {formatEpochTime(currentTimeMs)}
          </div>
        </div>
        <div className="px-3 py-1.5 rounded-xl bg-[#1C2433] border border-[#2D3B52] text-xs text-[#38BDF8] font-semibold flex items-center gap-1.5">
          <Clock className="w-3.5 h-3.5" />
          <span>סנכרון מדויק</span>
        </div>
      </section>

      {/* 2. Hero: When to Leave */}
      <section className="bg-[#131822] border border-[#00E676]/40 rounded-3xl p-5 mb-4 shadow-xl glow-green relative overflow-hidden">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-full bg-[#00E676]/15 flex items-center justify-center text-[#00E676]">
              <Navigation className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white leading-tight">מתי מומלץ לצאת לדרך?</h2>
              <p className="text-xs text-[#94A3B8]">תחזית גל ירוק רציף לשני הרמזורים</p>
            </div>
          </div>
          {departurePlan?.hasDirectGreenWave && (
            <span className="px-2.5 py-1 rounded-lg bg-[#00E676]/20 border border-[#00E676] text-[#00E676] text-xs font-bold flex items-center gap-1">
              <Zap className="w-3.5 h-3.5" />
              <span>גל ירוק כפול</span>
            </span>
          )}
        </div>

        {!departurePlan || !departurePlan.isAvailable ? (
          <div className="bg-[#1C2433] border border-[#2D3B52] rounded-2xl p-4 text-center">
            <AlertTriangle className="w-6 h-6 text-[#FFB020] mx-auto mb-1.5" />
            <p className="text-sm font-semibold text-white">אין מספיק נתונים לתחזית יציאה</p>
            <p className="text-xs text-[#94A3B8] mt-1">
              מדוד לפחות 2 מחזורים של רמזור 1, או הגדר זמנים ידנית במחשבון
            </p>
          </div>
        ) : (
          <div>
            <div
              className={`p-3.5 rounded-2xl mb-3 flex items-center justify-between ${
                departurePlan.secondsUntilDeparture <= 10
                  ? 'bg-[#00E676] text-black'
                  : 'bg-[#00E676]/15 border border-[#00E676]/40 text-[#00E676]'
              }`}
            >
              <div>
                <p className="text-xs font-medium opacity-80">זמן יציאה מומלץ</p>
                <p className="text-2xl font-black font-mono-numbers">
                  {formatEpochTime(departurePlan.recommendedDepartureEpochMs)}
                </p>
              </div>
              <div className="text-left font-bold text-sm">
                {departurePlan.secondsUntilDeparture <= 0
                  ? 'צא עכשיו! 🟢'
                  : `בעוד: ${formatSecondsToMinutes(departurePlan.secondsUntilDeparture)}`}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2 mb-2">
              <div className="bg-[#1C2433] border border-[#2D3B52] rounded-xl p-2.5">
                <span className="text-[11px] text-[#94A3B8]">הגעה לרמזור 1</span>
                <p className="text-sm font-bold font-mono-numbers text-white">
                  {formatEpochTime(departurePlan.arrivalLight1EpochMs)}
                </p>
                <span className="text-xs font-semibold text-[#00E676]">
                  {departurePlan.light1IsInGreen ? 'אור ירוק ✓' : 'אדום'}
                </span>
              </div>
              <div className="bg-[#1C2433] border border-[#2D3B52] rounded-xl p-2.5">
                <span className="text-[11px] text-[#94A3B8]">הגעה לרמזור 2</span>
                <p className="text-sm font-bold font-mono-numbers text-white">
                  {formatEpochTime(departurePlan.arrivalLight2EpochMs)}
                </p>
                <span
                  className={`text-xs font-semibold ${
                    departurePlan.light2IsInGreen ? 'text-[#00E676]' : 'text-[#FFB020]'
                  }`}
                >
                  {departurePlan.light2IsInGreen ? 'אור ירוק ✓' : 'המתנה קלה'}
                </span>
              </div>
            </div>

            <p className="text-xs text-[#94A3B8] leading-relaxed">{departurePlan.details}</p>
          </div>
        )}
      </section>

      {/* 3. Fast Measurement Quick Hub */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-3xl p-4 mb-4 shadow-lg">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="text-base font-bold text-white">מערכת מדידת רמזורים</h3>
            <p className="text-xs text-[#94A3B8]">לחץ בלייב ברגע שהרמזור מתחלף לירוק</p>
          </div>
          <span className="text-xs font-semibold px-2 py-1 rounded-md bg-[#1C2433] text-[#38BDF8]">
            {measurements.length} נמדדו
          </span>
        </div>

        <div className="grid grid-cols-2 gap-3 mb-2.5">
          <button
            onClick={() => handleRecordMeasurement('LIGHT_1', 'GREEN_START')}
            className="py-4 px-3 rounded-2xl bg-[#00E676] text-black font-extrabold text-sm active:scale-95 transition shadow-lg flex flex-col items-center justify-center gap-1.5"
          >
            <span className="w-3.5 h-3.5 rounded-full bg-black"></span>
            <span className="text-xs opacity-75 font-semibold">רמזור ראשון</span>
            <span className="text-base">הפך לירוק!</span>
          </button>

          <button
            onClick={() => handleRecordMeasurement('LIGHT_2', 'GREEN_START')}
            className="py-4 px-3 rounded-2xl bg-[#00E676] text-black font-extrabold text-sm active:scale-95 transition shadow-lg flex flex-col items-center justify-center gap-1.5"
          >
            <span className="w-3.5 h-3.5 rounded-full bg-black"></span>
            <span className="text-xs opacity-75 font-semibold">רמזור שני</span>
            <span className="text-base">הפך לירוק!</span>
          </button>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={() => handleRecordMeasurement('LIGHT_1', 'RED_START')}
            className="py-2 px-2 rounded-xl bg-[#1C2433] border border-[#2D3B52] text-xs font-semibold text-[#94A3B8] hover:text-[#FF385C] transition flex items-center justify-center gap-1.5"
          >
            <span className="w-2 h-2 rounded-full bg-[#FF385C]"></span>
            <span>רמזור 1 הפך לאדום</span>
          </button>

          <button
            onClick={() => handleRecordMeasurement('LIGHT_2', 'RED_START')}
            className="py-2 px-2 rounded-xl bg-[#1C2433] border border-[#2D3B52] text-xs font-semibold text-[#94A3B8] hover:text-[#FF385C] transition flex items-center justify-center gap-1.5"
          >
            <span className="w-2 h-2 rounded-full bg-[#FF385C]"></span>
            <span>רמזור 2 הפך לאדום</span>
          </button>
        </div>
      </section>

      {/* 4. Traffic Light 1 Card */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-md">
        <div className="flex items-center justify-between mb-3">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base font-bold text-white">רמזור ראשון</h3>
              <span
                className={`text-xs px-2 py-0.5 rounded font-bold ${
                  light1Analysis.isCurrentlyGreen
                    ? 'bg-[#00E676]/20 text-[#00E676]'
                    : 'bg-[#FF385C]/20 text-[#FF385C]'
                }`}
              >
                {light1Analysis.isCurrentlyGreen ? 'ירוק כעת' : 'אדום כעת'}
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">{light1Analysis.statusText}</p>
          </div>

          <div className="w-8 py-1.5 bg-[#0D1117] border border-[#2D3B52] rounded-xl flex flex-col items-center gap-1.5">
            <span
              className={`w-3.5 h-3.5 rounded-full ${
                !light1Analysis.isCurrentlyGreen ? 'bg-[#FF385C] shadow-[0_0_8px_#FF385C]' : 'bg-[#FF385C]/25'
              }`}
            ></span>
            <span className="w-3.5 h-3.5 rounded-full bg-[#FFB020]/25"></span>
            <span
              className={`w-3.5 h-3.5 rounded-full ${
                light1Analysis.isCurrentlyGreen ? 'bg-[#00E676] shadow-[0_0_8px_#00E676]' : 'bg-[#00E676]/25'
              }`}
            ></span>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 text-center bg-[#1C2433] rounded-xl p-2.5 mb-2.5">
          <div>
            <span className="text-[11px] text-[#94A3B8]">אורך מחזור</span>
            <p className="text-xs font-bold text-white">
              {light1Analysis.cycleDurationSeconds ? `${Math.round(light1Analysis.cycleDurationSeconds)} ש׳` : 'לא ידוע'}
            </p>
          </div>
          <div>
            <span className="text-[11px] text-[#94A3B8]">משך ירוק</span>
            <p className="text-xs font-bold text-white">
              {Math.round(light1Analysis.greenDurationSeconds)} ש׳
            </p>
          </div>
          <div>
            <span className="text-[11px] text-[#94A3B8]">ביטחון תחזית</span>
            <p className="text-xs font-bold text-[#00E676]">{light1Analysis.confidencePercent}%</p>
          </div>
        </div>

        <div className="w-full bg-[#1C2433] h-1.5 rounded-full overflow-hidden">
          <div
            className="bg-[#00E676] h-full transition-all duration-300"
            style={{ width: `${light1Analysis.confidencePercent}%` }}
          ></div>
        </div>
      </section>

      {/* 5. Traffic Light 2 Card */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-md">
        <div className="flex items-center justify-between mb-3">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base font-bold text-white">רמזור שני</h3>
              <span
                className={`text-xs px-2 py-0.5 rounded font-bold ${
                  light2Analysis.isCurrentlyGreen
                    ? 'bg-[#00E676]/20 text-[#00E676]'
                    : 'bg-[#FF385C]/20 text-[#FF385C]'
                }`}
              >
                {light2Analysis.isCurrentlyGreen ? 'ירוק כעת' : 'אדום כעת'}
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">{light2Analysis.statusText}</p>
          </div>

          <div className="w-8 py-1.5 bg-[#0D1117] border border-[#2D3B52] rounded-xl flex flex-col items-center gap-1.5">
            <span
              className={`w-3.5 h-3.5 rounded-full ${
                !light2Analysis.isCurrentlyGreen ? 'bg-[#FF385C] shadow-[0_0_8px_#FF385C]' : 'bg-[#FF385C]/25'
              }`}
            ></span>
            <span className="w-3.5 h-3.5 rounded-full bg-[#FFB020]/25"></span>
            <span
              className={`w-3.5 h-3.5 rounded-full ${
                light2Analysis.isCurrentlyGreen ? 'bg-[#00E676] shadow-[0_0_8px_#00E676]' : 'bg-[#00E676]/25'
              }`}
            ></span>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 text-center bg-[#1C2433] rounded-xl p-2.5 mb-2.5">
          <div>
            <span className="text-[11px] text-[#94A3B8]">אורך מחזור</span>
            <p className="text-xs font-bold text-white">
              {light2Analysis.cycleDurationSeconds ? `${Math.round(light2Analysis.cycleDurationSeconds)} ש׳` : 'לא ידוע'}
            </p>
          </div>
          <div>
            <span className="text-[11px] text-[#94A3B8]">משך ירוק</span>
            <p className="text-xs font-bold text-white">
              {Math.round(light2Analysis.greenDurationSeconds)} ש׳
            </p>
          </div>
          <div>
            <span className="text-[11px] text-[#94A3B8]">ביטחון תחזית</span>
            <p className="text-xs font-bold text-[#00E676]">{light2Analysis.confidencePercent}%</p>
          </div>
        </div>

        <div className="w-full bg-[#1C2433] h-1.5 rounded-full overflow-hidden">
          <div
            className="bg-[#00E676] h-full transition-all duration-300"
            style={{ width: `${light2Analysis.confidencePercent}%` }}
          ></div>
        </div>
      </section>

      {/* 6. Sync Analysis Card */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-md flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Footprints className="w-4 h-4 text-[#38BDF8]" />
            <h4 className="text-sm font-bold text-white">סנכרון בין הרמזורים</h4>
          </div>
          <p className="text-xs text-[#94A3B8] leading-relaxed">{syncAnalysis.statusDescription}</p>
        </div>
        <span
          className={`text-xs px-2.5 py-1 rounded-lg font-bold shrink-0 ${
            syncAnalysis.isSynchronized ? 'bg-[#00E676]/20 text-[#00E676]' : 'bg-[#1C2433] text-[#94A3B8]'
          }`}
        >
          {syncAnalysis.statusBadge}
        </span>
      </section>

      {/* 7. Walking Settings */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-md">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Footprints className="w-4 h-4 text-[#38BDF8]" />
            <h3 className="text-sm font-bold text-white">מחשבון זמני הליכה</h3>
          </div>
          <span className="text-xs font-bold text-[#38BDF8]">
            סה״כ: {walkingSettings.walkToLight1Seconds + walkingSettings.walkBetweenLightsSeconds + walkingSettings.safetyBufferSeconds} ש׳
          </span>
        </div>

        <div className="space-y-3">
          <div>
            <div className="flex justify-between text-xs mb-1">
              <span className="text-[#94A3B8]">זמן הליכה מהמקום לרמזור 1:</span>
              <span className="font-bold text-white font-mono-numbers">
                {walkingSettings.walkToLight1Seconds} שניות
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkToLight1Seconds: Math.max(10, prev.walkToLight1Seconds - 10)
                  }))
                }
                className="w-8 h-8 rounded-lg bg-[#1C2433] flex items-center justify-center text-white"
              >
                <Minus className="w-3.5 h-3.5" />
              </button>
              <input
                type="range"
                min="10"
                max="300"
                step="5"
                value={walkingSettings.walkToLight1Seconds}
                onChange={(e) =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkToLight1Seconds: Number(e.target.value)
                  }))
                }
                className="flex-1 accent-[#00E676]"
              />
              <button
                onClick={() =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkToLight1Seconds: Math.min(600, prev.walkToLight1Seconds + 10)
                  }))
                }
                className="w-8 h-8 rounded-lg bg-[#1C2433] flex items-center justify-center text-white"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <span className="text-[#94A3B8]">זמן הליכה מרמזור 1 לרמזור 2:</span>
              <span className="font-bold text-white font-mono-numbers">
                {walkingSettings.walkBetweenLightsSeconds} שניות
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkBetweenLightsSeconds: Math.max(10, prev.walkBetweenLightsSeconds - 10)
                  }))
                }
                className="w-8 h-8 rounded-lg bg-[#1C2433] flex items-center justify-center text-white"
              >
                <Minus className="w-3.5 h-3.5" />
              </button>
              <input
                type="range"
                min="10"
                max="300"
                step="5"
                value={walkingSettings.walkBetweenLightsSeconds}
                onChange={(e) =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkBetweenLightsSeconds: Number(e.target.value)
                  }))
                }
                className="flex-1 accent-[#00E676]"
              />
              <button
                onClick={() =>
                  setWalkingSettings((prev) => ({
                    ...prev,
                    walkBetweenLightsSeconds: Math.min(600, prev.walkBetweenLightsSeconds + 10)
                  }))
                }
                className="w-8 h-8 rounded-lg bg-[#1C2433] flex items-center justify-center text-white"
              >
                <Plus className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* 8. Manual Override Section */}
      <section className="bg-[#131822] border border-[#2D3B52] rounded-2xl p-4 mb-4 shadow-md">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Sliders className="w-4 h-4 text-[#FFB020]" />
            <div>
              <h3 className="text-sm font-bold text-white">הזנת זמנים ידנית</h3>
              <p className="text-[11px] text-[#94A3B8]">הגדר מחזורים קבועים</p>
            </div>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={manualConfig.isEnabled}
              onChange={(e) => setManualConfig((prev) => ({ ...prev, isEnabled: e.target.checked }))}
              className="sr-only peer"
            />
            <div className="w-11 h-6 bg-[#1C2433] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00E676]"></div>
          </label>
        </div>

        {manualConfig.isEnabled && (
          <div className="space-y-3 pt-2">
            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="text-[11px] text-[#94A3B8]">מחזור 1 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light1CycleSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light1CycleSeconds: Number(e.target.value) || 60
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
              <div>
                <label className="text-[11px] text-[#94A3B8]">ירוק 1 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light1GreenDurationSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light1GreenDurationSeconds: Number(e.target.value) || 20
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
              <div>
                <label className="text-[11px] text-[#94A3B8]">היסט 1 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light1OffsetSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light1OffsetSeconds: Number(e.target.value) || 0
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="text-[11px] text-[#94A3B8]">מחזור 2 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light2CycleSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light2CycleSeconds: Number(e.target.value) || 60
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
              <div>
                <label className="text-[11px] text-[#94A3B8]">ירוק 2 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light2GreenDurationSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light2GreenDurationSeconds: Number(e.target.value) || 20
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
              <div>
                <label className="text-[11px] text-[#94A3B8]">היסט 2 (ש׳)</label>
                <input
                  type="number"
                  value={manualConfig.light2OffsetSeconds}
                  onChange={(e) =>
                    setManualConfig((prev) => ({
                      ...prev,
                      light2OffsetSeconds: Number(e.target.value) || 30
                    }))
                  }
                  className="w-full bg-[#1C2433] border border-[#2D3B52] rounded-lg p-2 text-xs font-mono text-white"
                />
              </div>
            </div>
          </div>
        )}
      </section>

      {/* 9. Safety Disclaimer */}
      <section className="bg-[#131822]/60 border border-[#2D3B52] rounded-2xl p-3 flex items-center gap-2.5">
        <ShieldCheck className="w-5 h-5 text-[#FFB020] shrink-0" />
        <p className="text-[11px] text-[#94A3B8] leading-tight">
          התחזיות הן הערכות בלבד. יש לציית לרמזורים בפועל ולחצות רק באור ירוק, לאחר בדיקת הדרך.
        </p>
      </section>

      {/* History Dialog Modal */}
      {showHistory && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="bg-[#131822] border border-[#2D3B52] rounded-3xl p-5 max-w-sm w-full max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-bold text-white flex items-center gap-2">
                <History className="w-4 h-4 text-[#38BDF8]" />
                <span>היסטוריית מדידות ({measurements.length})</span>
              </h3>
              <button
                onClick={() => setShowHistory(false)}
                className="text-xs text-[#94A3B8] hover:text-white"
              >
                סגור
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 pr-1 mb-4">
              {measurements.length === 0 ? (
                <p className="text-xs text-[#94A3B8] text-center py-6">אין עדיין מדידות שמורות</p>
              ) : (
                measurements
                  .slice()
                  .reverse()
                  .map((item) => (
                    <div
                      key={item.id}
                      className="bg-[#1C2433] rounded-xl p-2.5 flex items-center justify-between"
                    >
                      <div className="flex items-center gap-2">
                        <span
                          className={`w-2 h-2 rounded-full ${
                            item.type === 'GREEN_START' ? 'bg-[#00E676]' : 'bg-[#FF385C]'
                          }`}
                        ></span>
                        <div>
                          <p className="text-xs font-bold text-white">
                            {item.lightId === 'LIGHT_1' ? 'רמזור 1' : 'רמזור 2'} –{' '}
                            {item.type === 'GREEN_START' ? 'הפך לירוק' : 'הפך לאדום'}
                          </p>
                          <p className="text-[11px] font-mono text-[#94A3B8]">
                            {formatEpochTime(item.timestamp)}
                          </p>
                        </div>
                      </div>
                      <button
                        onClick={() => handleDeleteMeasurement(item.id)}
                        className="text-[#94A3B8] hover:text-[#FF385C] p-1"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))
              )}
            </div>

            <div className="flex gap-2">
              {measurements.length > 0 && (
                <button
                  onClick={handleClearAll}
                  className="flex-1 py-2 rounded-xl bg-[#FF385C]/20 border border-[#FF385C]/40 text-[#FF385C] font-semibold text-xs"
                >
                  מחק הכל
                </button>
              )}
              <button
                onClick={() => setShowHistory(false)}
                className="flex-1 py-2 rounded-xl bg-[#00E676] text-black font-bold text-xs"
              >
                אישור
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
