# mobile-measure

현장 측정(Android) 전용 레포입니다.

## Directory

- `android-app/`: Kotlin 앱 소스
- `docs/`: 요구사항/센서 정책/권한 문서
- `shared-contract-notes/`: web-platform 계약 연동 메모

## Scope

- RSSI/BSSID/지연/속도 수집
- 측정 세션 업로드
- 최소 결과 확인 뷰

## Integration Rule

- 계약 원본은 `web-platform/shared/` 기준
- 이 레포는 계약 소비자(consumer)로 동작
