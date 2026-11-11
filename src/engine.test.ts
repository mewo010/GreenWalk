import { describe, it, expect } from 'vitest';
import {
  analyzeLight,
  analyzeSync,
  calculateDeparturePlan,
  formatEpochTime,
  formatSecondsToMinutes
} from './engine';
import { ManualConfig, MeasurementEvent, WalkingSettings } from './types';

describe('GreenWalk Engine Tests', () => {
  it('handles empty and single measurement safely', () => {
    const now = 1700000000000;
    const manual: ManualConfig = {
      isEnabled: false,
      light1CycleSeconds: 80,
      light1GreenDurationSeconds: 20,
      light1OffsetSeconds: 0,
      light2CycleSeconds: 80,
      light2GreenDurationSeconds: 20,
      light2OffsetSeconds: 0
    };

    const empty = analyzeLight([], 'LIGHT_1', now, manual);
    expect(empty.cycleDurationSeconds).toBeNull();
    expect(empty.confidencePercent).toBe(0);

    const singleEvent: MeasurementEvent = {
      id: '1',
      lightId: 'LIGHT_1',
      timestamp: now,
      type: 'GREEN_START'
    };

    const single = analyzeLight([singleEvent], 'LIGHT_1', now + 5000, manual);
    expect(single.cycleDurationSeconds).toBeNull();
    expect(single.isCurrentlyGreen).toBe(true);
  });

  it('calculates consistent cycles and confidence score', () => {
    const base = 1700000000000;
    const cycle = 75000; // 75s
    const manual: ManualConfig = {
      isEnabled: false,
      light1CycleSeconds: 80,
      light1GreenDurationSeconds: 20,
      light1OffsetSeconds: 0,
      light2CycleSeconds: 80,
      light2GreenDurationSeconds: 20,
      light2OffsetSeconds: 0
    };

    const events: MeasurementEvent[] = [
      { id: '1', lightId: 'LIGHT_1', timestamp: base, type: 'GREEN_START' },
      { id: '2', lightId: 'LIGHT_1', timestamp: base + cycle, type: 'GREEN_START' },
      { id: '3', lightId: 'LIGHT_1', timestamp: base + 2 * cycle, type: 'GREEN_START' },
      { id: '4', lightId: 'LIGHT_1', timestamp: base + 3 * cycle, type: 'GREEN_START' }
    ];

    const res = analyzeLight(events, 'LIGHT_1', base + 3 * cycle + 5000, manual);
    expect(res.cycleDurationSeconds).toBeCloseTo(75, 1);
    expect(res.confidencePercent).toBeGreaterThanOrEqual(75);
    expect(res.isCurrentlyGreen).toBe(true);
  });

  it('handles midnight rollover cleanly', () => {
    const nearMidnight = 1700092740000; // 23:59:00
    const afterMidnight = nearMidnight + 160000; // 00:01:40
    const manual: ManualConfig = {
      isEnabled: false,
      light1CycleSeconds: 80,
      light1GreenDurationSeconds: 20,
      light1OffsetSeconds: 0,
      light2CycleSeconds: 80,
      light2GreenDurationSeconds: 20,
      light2OffsetSeconds: 0
    };

    const events: MeasurementEvent[] = [
      { id: '1', lightId: 'LIGHT_1', timestamp: nearMidnight, type: 'GREEN_START' },
      { id: '2', lightId: 'LIGHT_1', timestamp: nearMidnight + 80000, type: 'GREEN_START' },
      { id: '3', lightId: 'LIGHT_1', timestamp: afterMidnight, type: 'GREEN_START' }
    ];

    const res = analyzeLight(events, 'LIGHT_1', afterMidnight + 10000, manual);
    expect(res.cycleDurationSeconds).toBeCloseTo(80, 1);
    expect(res.confidencePercent).toBeGreaterThan(50);
  });

  it('calculates departure plan and green wave sync', () => {
    const base = 1700000000000;
    const cycle = 80000;
    const manual: ManualConfig = {
      isEnabled: false,
      light1CycleSeconds: 80,
      light1GreenDurationSeconds: 20,
      light1OffsetSeconds: 0,
      light2CycleSeconds: 80,
      light2GreenDurationSeconds: 20,
      light2OffsetSeconds: 0
    };

    const events: MeasurementEvent[] = [
      { id: '1', lightId: 'LIGHT_1', timestamp: base, type: 'GREEN_START' },
      { id: '2', lightId: 'LIGHT_1', timestamp: base + cycle, type: 'GREEN_START' },
      { id: '3', lightId: 'LIGHT_2', timestamp: base + cycle, type: 'GREEN_START' },
      { id: '4', lightId: 'LIGHT_2', timestamp: base + 2 * cycle, type: 'GREEN_START' }
    ];

    const l1 = analyzeLight(events, 'LIGHT_1', base + 5000, manual);
    const l2 = analyzeLight(events, 'LIGHT_2', base + 5000, manual);
    const walking: WalkingSettings = {
      walkToLight1Seconds: 60,
      walkBetweenLightsSeconds: 80,
      safetyBufferSeconds: 0
    };

    const plan = calculateDeparturePlan(l1, l2, walking, base + 5000);
    expect(plan).not.toBeNull();
    expect(plan?.isAvailable).toBe(true);
    expect(plan?.light1IsInGreen).toBe(true);
  });

  it('formats seconds to mm:ss correctly', () => {
    expect(formatSecondsToMinutes(0)).toBe('00:00');
    expect(formatSecondsToMinutes(65)).toBe('01:05');
    expect(formatSecondsToMinutes(3600)).toBe('60:00');
  });
});
