import {
  DeparturePlan,
  ManualConfig,
  MeasurementEvent,
  SyncAnalysis,
  TrafficLightAnalysis,
  TrafficLightId,
  WalkingSettings
} from './types';

export function formatEpochTime(epochMs: number): string {
  const d = new Date(epochMs);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function formatSecondsToMinutes(totalSeconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${pad(minutes)}:${pad(seconds)}`;
}

export function analyzeLight(
  measurements: MeasurementEvent[],
  lightId: TrafficLightId,
  currentTimeMs: number,
  manualConfig: ManualConfig
): TrafficLightAnalysis {
  if (manualConfig.isEnabled) {
    const cycleSec = lightId === 'LIGHT_1' ? manualConfig.light1CycleSeconds : manualConfig.light2CycleSeconds;
    const greenSec = lightId === 'LIGHT_1' ? manualConfig.light1GreenDurationSeconds : manualConfig.light2GreenDurationSeconds;
    const offsetSec = lightId === 'LIGHT_1' ? manualConfig.light1OffsetSeconds : manualConfig.light2OffsetSeconds;

    const cycleMs = Math.max(5000, cycleSec * 1000);
    const greenMs = Math.max(2000, Math.min(cycleMs - 2000, greenSec * 1000));

    const startOfDay = new Date(currentTimeMs);
    startOfDay.setHours(0, 0, 0, 0);
    const baseTodayMs = startOfDay.getTime() + offsetSec * 1000;
    const elapsed = currentTimeMs - baseTodayMs;
    const posInCycle = ((elapsed % cycleMs) + cycleMs) % cycleMs;

    const isGreen = posInCycle < greenMs;
    const remainingGreenSec = isGreen ? (greenMs - posInCycle) / 1000 : null;
    const timeToNextGreenSec = isGreen ? 0 : (cycleMs - posInCycle) / 1000;
    const nextGreenMs = currentTimeMs + (cycleMs - posInCycle);

    return {
      lightId,
      cycleDurationSeconds: cycleSec,
      greenDurationSeconds: greenSec,
      cycleCount: 10,
      confidencePercent: 100,
      lastGreenEpochMs: currentTimeMs - posInCycle,
      nextGreenEpochMs: nextGreenMs,
      isCurrentlyGreen: isGreen,
      remainingGreenSeconds: remainingGreenSec,
      timeUntilNextGreenSeconds: timeToNextGreenSec,
      statusText: isGreen ? 'ירוק כעת (הזנה ידנית)' : 'אדום כעת (הזנה ידנית)',
      cycleStdDev: 0
    };
  }

  const lightEvents = measurements
    .filter((m) => m.lightId === lightId)
    .sort((a, b) => a.timestamp - b.timestamp);

  const greenEvents = lightEvents.filter((m) => m.type === 'GREEN_START');
  const redEvents = lightEvents.filter((m) => m.type === 'RED_START');

  if (greenEvents.length === 0) {
    return {
      lightId,
      cycleDurationSeconds: null,
      greenDurationSeconds: 20,
      cycleCount: 0,
      confidencePercent: 0,
      lastGreenEpochMs: null,
      nextGreenEpochMs: null,
      isCurrentlyGreen: false,
      remainingGreenSeconds: null,
      timeUntilNextGreenSeconds: null,
      statusText: 'אין עדיין מדידות לרמזור זה',
      cycleStdDev: 0
    };
  }

  const lastGreenEvent = greenEvents[greenEvents.length - 1];

  if (greenEvents.length === 1) {
    const elapsedSec = (currentTimeMs - lastGreenEvent.timestamp) / 1000;
    const isGreen = elapsedSec < 20;
    return {
      lightId,
      cycleDurationSeconds: null,
      greenDurationSeconds: 20,
      cycleCount: 1,
      confidencePercent: 25,
      lastGreenEpochMs: lastGreenEvent.timestamp,
      nextGreenEpochMs: null,
      isCurrentlyGreen: isGreen,
      remainingGreenSeconds: isGreen ? Math.max(0, 20 - elapsedSec) : null,
      timeUntilNextGreenSeconds: null,
      statusText: 'נמדדה פעם אחת - דרושה מדידה נוספת לחישוב מחזור',
      cycleStdDev: 0
    };
  }

  const rawIntervalsSec: number[] = [];
  for (let i = 0; i < greenEvents.length - 1; i++) {
    const deltaMs = greenEvents[i + 1].timestamp - greenEvents[i].timestamp;
    if (deltaMs > 5000) {
      rawIntervalsSec.push(deltaMs / 1000);
    }
  }

  if (rawIntervalsSec.length === 0) {
    return {
      lightId,
      cycleDurationSeconds: null,
      greenDurationSeconds: 20,
      cycleCount: greenEvents.length,
      confidencePercent: 10,
      lastGreenEpochMs: lastGreenEvent.timestamp,
      nextGreenEpochMs: null,
      isCurrentlyGreen: false,
      remainingGreenSeconds: null,
      timeUntilNextGreenSeconds: null,
      statusText: 'מדידות קרובות מדי - נסה שוב',
      cycleStdDev: 0
    };
  }

  const minInterval = Math.min(...rawIntervalsSec);
  const normalizedIntervals = rawIntervalsSec.map((interval) => {
    const ratio = Math.round(interval / minInterval);
    if (ratio >= 2 && ratio <= 4 && Math.abs(interval - ratio * minInterval) < minInterval * 0.35) {
      return interval / ratio;
    }
    return interval;
  });

  const meanCycleSec = normalizedIntervals.reduce((a, b) => a + b, 0) / normalizedIntervals.length;
  const variance =
    normalizedIntervals.reduce((sum, val) => sum + Math.pow(val - meanCycleSec, 2), 0) /
    normalizedIntervals.length;
  const stdDev = Math.sqrt(variance);

  const relDeviation = meanCycleSec > 0 ? stdDev / meanCycleSec : 1.0;
  const consistencyFactor = Math.max(0, 1 - relDeviation);
  const countFactor = Math.min(1.0, normalizedIntervals.length / 3.0);
  const confidencePercent = Math.min(99, Math.max(10, Math.round((consistencyFactor * 0.7 + countFactor * 0.3) * 100)));

  let learnedGreenDurationSec = 20;
  const pairedGreenDurations: number[] = [];
  for (const green of greenEvents) {
    const nextRed = redEvents.find((r) => r.timestamp > green.timestamp);
    if (nextRed) {
      const nextGreen = greenEvents.find((g) => g.timestamp > green.timestamp);
      if (!nextGreen || nextRed.timestamp < nextGreen.timestamp) {
        const durMs = nextRed.timestamp - green.timestamp;
        if (durMs >= 3000 && durMs <= 90000) {
          pairedGreenDurations.push(durMs / 1000);
        }
      }
    }
  }
  if (pairedGreenDurations.length > 0) {
    learnedGreenDurationSec =
      pairedGreenDurations.reduce((a, b) => a + b, 0) / pairedGreenDurations.length;
  }

  const cycleMs = Math.max(10000, Math.round(meanCycleSec * 1000));
  const greenMs = Math.max(3000, Math.min(cycleMs - 3000, Math.round(learnedGreenDurationSec * 1000)));

  const elapsedSinceLast = currentTimeMs - lastGreenEvent.timestamp;
  const posInCycle = ((elapsedSinceLast % cycleMs) + cycleMs) % cycleMs;

  const isGreen = posInCycle < greenMs;
  const remainingGreenSec = isGreen ? (greenMs - posInCycle) / 1000 : null;
  const timeToNextGreenSec = (cycleMs - posInCycle) / 1000;
  const nextGreenMs = currentTimeMs + (cycleMs - posInCycle);

  const statusText = isGreen
    ? `ירוק כעת! נותרו כ-${Math.round(remainingGreenSec ?? 0)} שניות`
    : timeToNextGreenSec < 10
    ? `עומד להתחלף לירוק בקרוב (${Math.round(timeToNextGreenSec)} שניות)`
    : `אדום כעת (${Math.round(timeToNextGreenSec)} שניות לירוק הבא)`;

  return {
    lightId,
    cycleDurationSeconds: meanCycleSec,
    greenDurationSeconds: learnedGreenDurationSec,
    cycleCount: greenEvents.length,
    confidencePercent,
    lastGreenEpochMs: lastGreenEvent.timestamp,
    nextGreenEpochMs: nextGreenMs,
    isCurrentlyGreen: isGreen,
    remainingGreenSeconds: remainingGreenSec,
    timeUntilNextGreenSeconds: timeToNextGreenSec,
    statusText,
    cycleStdDev: stdDev
  };
}

export function analyzeSync(
  light1: TrafficLightAnalysis,
  light2: TrafficLightAnalysis,
  walkingSettings: WalkingSettings
): SyncAnalysis {
  const l1Last = light1.lastGreenEpochMs;
  const l2Last = light2.lastGreenEpochMs;
  const cycle1 = light1.cycleDurationSeconds;
  const cycle2 = light2.cycleDurationSeconds;

  if (!l1Last || !l2Last || !cycle1 || !cycle2) {
    return {
      offsetSeconds: null,
      walkTimeSeconds: walkingSettings.walkBetweenLightsSeconds,
      isSynchronized: false,
      statusDescription: 'נדרשות מדידות של שני הרמזורים לחישוב סנכרון',
      statusBadge: 'נתונים חסרים'
    };
  }

  const avgCycle = (cycle1 + cycle2) / 2.0;
  const cycleDiff = Math.abs(cycle1 - cycle2);
  if (cycleDiff > avgCycle * 0.35) {
    return {
      offsetSeconds: null,
      walkTimeSeconds: walkingSettings.walkBetweenLightsSeconds,
      isSynchronized: false,
      statusDescription: `מחזורי הרמזורים שונים משמעותית (${Math.round(cycle1)} ש׳ לעומת ${Math.round(cycle2)} ש׳). הסנכרון ישתנה.`,
      statusBadge: 'מחזורים שונים'
    };
  }

  const diffMs = l2Last - l1Last;
  const cycleMs = avgCycle * 1000;
  const offsetMs = ((diffMs % cycleMs) + cycleMs) % cycleMs;
  const offsetSec = offsetMs / 1000;

  const walkSec = walkingSettings.walkBetweenLightsSeconds;
  const expectedArrivalOffset = offsetSec - walkSec;
  const greenWindow = light2.greenDurationSeconds;

  const normalizedArrivalInCycle = ((expectedArrivalOffset % avgCycle) + avgCycle) % avgCycle;
  const isSync = normalizedArrivalInCycle <= greenWindow || avgCycle - normalizedArrivalInCycle <= 3.0;

  return {
    offsetSeconds: offsetSec,
    walkTimeSeconds: walkingSettings.walkBetweenLightsSeconds,
    isSynchronized: isSync,
    statusDescription: isSync
      ? `הפרש ירוק: ${Math.round(offsetSec)} ש׳. זמן הליכה: ${Math.round(walkSec)} ש׳. מגיעים בדיוק בירוק!`
      : `הפרש ירוק: ${Math.round(offsetSec)} ש׳. זמן הליכה: ${Math.round(walkSec)} ש׳. צפויה המתנה קלה.`,
    statusBadge: isSync ? 'גל ירוק רציף ⚡' : 'המתנה חלקית'
  };
}

export function calculateDeparturePlan(
  light1: TrafficLightAnalysis,
  light2: TrafficLightAnalysis,
  walking: WalkingSettings,
  currentTimeMs: number
): DeparturePlan | null {
  const cycle1 = light1.cycleDurationSeconds;
  const l1Last = light1.lastGreenEpochMs;
  const greenDur1 = light1.greenDurationSeconds;

  if (!cycle1 || !l1Last) return null;

  const cycle2 = light2.cycleDurationSeconds;
  const l2Last = light2.lastGreenEpochMs;
  const greenDur2 = light2.greenDurationSeconds;

  const walk1Sec = walking.walkToLight1Seconds;
  const bufferSec = walking.safetyBufferSeconds;
  const walkBetweenSec = walking.walkBetweenLightsSeconds;
  const minDeparturePrepMs = 4000;

  const c1Ms = cycle1 * 1000;
  const g1Ms = greenDur1 * 1000;

  if (!cycle2 || !l2Last) {
    for (let k = 0; k <= 15; k++) {
      const greenStartMs = l1Last + k * c1Ms;
      const targetArrival = greenStartMs + g1Ms / 3;
      const requiredDeparture = targetArrival - (walk1Sec + bufferSec) * 1000;
      if (requiredDeparture >= currentTimeMs + minDeparturePrepMs) {
        const arr1 = targetArrival;
        const arr2 = arr1 + walkBetweenSec * 1000;
        const remainingSec = Math.floor((requiredDeparture - currentTimeMs) / 1000);

        return {
          recommendedDepartureEpochMs: requiredDeparture,
          secondsUntilDeparture: Math.max(0, remainingSec),
          arrivalLight1EpochMs: arr1,
          arrivalLight2EpochMs: arr2,
          light1IsInGreen: true,
          light2IsInGreen: false,
          hasDirectGreenWave: false,
          waitAtLight1Seconds: 0,
          summary: `יציאה מומלצת: ${formatEpochTime(requiredDeparture)} (בעוד ${formatSecondsToMinutes(remainingSec)})`,
          details: `הגעה לרמזור 1 בירוק (${formatEpochTime(arr1)}). מדוד את רמזור 2 לחישוב גל ירוק לשניהם.`,
          isAvailable: true
        };
      }
    }
    return null;
  }

  const c2Ms = cycle2 * 1000;
  const g2Ms = greenDur2 * 1000;

  let bestWavePlan: DeparturePlan | null = null;
  let bestAlternativePlan: DeparturePlan | null = null;
  let lowestWaitAtLight1 = Number.MAX_SAFE_INTEGER;

  const horizonMs = currentTimeMs + 35 * 60 * 1000;
  const kMax = Math.floor((horizonMs - l1Last) / c1Ms) + 2;

  for (let k = 0; k <= kMax; k++) {
    const g1Start = l1Last + k * c1Ms;
    for (const stepFrac of [0.2, 0.5, 0.8]) {
      const targetArr1 = g1Start + g1Ms * stepFrac;
      const candidateDep = targetArr1 - (walk1Sec + bufferSec) * 1000;

      if (candidateDep < currentTimeMs + minDeparturePrepMs) continue;

      const targetArr2 = targetArr1 + walkBetweenSec * 1000;
      const l2CyclePos = ((targetArr2 - l2Last) % c2Ms + c2Ms) % c2Ms;

      const isL2InGreen = l2CyclePos >= 0 && l2CyclePos <= g2Ms;
      const remainingSec = Math.floor((candidateDep - currentTimeMs) / 1000);

      if (isL2InGreen) {
        const plan: DeparturePlan = {
          recommendedDepartureEpochMs: candidateDep,
          secondsUntilDeparture: remainingSec,
          arrivalLight1EpochMs: targetArr1,
          arrivalLight2EpochMs: targetArr2,
          light1IsInGreen: true,
          light2IsInGreen: true,
          hasDirectGreenWave: true,
          waitAtLight1Seconds: 0,
          summary: `גל ירוק כפול! צא ב-${formatEpochTime(candidateDep)}`,
          details: `הגעה לרמזור 1 ב-${formatEpochTime(targetArr1)} (ירוק), המשך רציף לרמזור 2 ב-${formatEpochTime(targetArr2)} (ירוק). ללא המתנה!`,
          isAvailable: true
        };
        if (!bestWavePlan || plan.secondsUntilDeparture < bestWavePlan.secondsUntilDeparture) {
          bestWavePlan = plan;
        }
      } else {
        const waitMs = l2CyclePos < g2Ms ? 0 : c2Ms - l2CyclePos;
        const waitSec = Math.floor(waitMs / 1000);

        if (waitSec < lowestWaitAtLight1 && !bestAlternativePlan) {
          lowestWaitAtLight1 = waitSec;
          bestAlternativePlan = {
            recommendedDepartureEpochMs: candidateDep,
            secondsUntilDeparture: remainingSec,
            arrivalLight1EpochMs: targetArr1,
            arrivalLight2EpochMs: targetArr2 + waitMs,
            light1IsInGreen: true,
            light2IsInGreen: true,
            hasDirectGreenWave: false,
            waitAtLight1Seconds: waitSec,
            summary: `יציאה מומלצת: ${formatEpochTime(candidateDep)} (המתנה קצרה)`,
            details: `הגעה לרמזור 1 ב-${formatEpochTime(targetArr1)} בירוק. המתנה קלה של כ-${waitSec} ש׳, והגעה לרמזור 2 בירוק.`,
            isAvailable: true
          };
        }
      }
    }

    if (bestWavePlan) break;
  }

  return bestWavePlan || bestAlternativePlan;
}
