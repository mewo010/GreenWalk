export type TrafficLightId = 'LIGHT_1' | 'LIGHT_2';
export type MeasurementType = 'GREEN_START' | 'RED_START';

export interface MeasurementEvent {
  id: string;
  lightId: TrafficLightId;
  timestamp: number;
  type: MeasurementType;
}

export interface TrafficLightAnalysis {
  lightId: TrafficLightId;
  cycleDurationSeconds: number | null;
  greenDurationSeconds: number;
  cycleCount: number;
  confidencePercent: number;
  lastGreenEpochMs: number | null;
  nextGreenEpochMs: number | null;
  isCurrentlyGreen: boolean;
  remainingGreenSeconds: number | null;
  timeUntilNextGreenSeconds: number | null;
  statusText: string;
  cycleStdDev: number;
}

export interface WalkingSettings {
  walkToLight1Seconds: number;
  walkBetweenLightsSeconds: number;
  safetyBufferSeconds: number;
}

export interface ManualConfig {
  isEnabled: boolean;
  light1CycleSeconds: number;
  light1GreenDurationSeconds: number;
  light1OffsetSeconds: number;
  light2CycleSeconds: number;
  light2GreenDurationSeconds: number;
  light2OffsetSeconds: number;
}

export interface DeparturePlan {
  recommendedDepartureEpochMs: number;
  secondsUntilDeparture: number;
  arrivalLight1EpochMs: number;
  arrivalLight2EpochMs: number;
  light1IsInGreen: boolean;
  light2IsInGreen: boolean;
  hasDirectGreenWave: boolean;
  waitAtLight1Seconds: number;
  summary: string;
  details: string;
  isAvailable: boolean;
}

export interface SyncAnalysis {
  offsetSeconds: number | null;
  walkTimeSeconds: number;
  isSynchronized: boolean;
  statusDescription: string;
  statusBadge: string;
}
