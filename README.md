# 하이웍스 자동 출퇴근 체크

하이웍스 출근/퇴근을 텔레그램 봇으로 제어하는 자동화 프로그램입니다.  
매일 7:30에 알림을 보내고, 선택(또는 전자결재 감지)에 따라 출퇴근을 자동 처리합니다.

---

## 동작 흐름

| 시각 | 동작 |
|---|---|
| 07:25 | Windows 작업 스케줄러 → 프로그램 자동 시작 |
| 07:30 | 전자결재 확인 → 연차/반차 감지 시 자동 설정 메시지 전송 (버튼 미발송) |
| 07:30 | 전자결재 미감지 시 텔레그램 버튼 메시지 발송 |
| 이후 | 선택된 유형에 따라 출퇴근 자동 처리 |
| 18:15 | 프로그램 자동 종료 |

> 공휴일은 "오늘은 공휴일" 안내 후 모든 체크 건너뜀

---

## 공휴일 처리

별도 설정 없이 한국 공휴일을 자동 감지합니다.  
[date.nager.at](https://date.nager.at) API를 사용하며 API 키가 필요 없습니다.  
공휴일에는 "🎉 오늘은 공휴일입니다" 메시지를 보내고 출퇴근 체크를 전부 건너뜁니다.

---

## 출퇴근 옵션

| 유형 | 출근 | 퇴근 | 설정 방법 |
|---|---|---|---|
| 8시 출근 | 8시 | 17시 | 버튼 선택 또는 무응답(기본값) |
| 9시 출근 | 9시 | 18시 | 버튼 선택 |
| 🌄 오전반차 (1시 출근) | 1시 | 17시 | 버튼 선택 또는 전자결재 자동 감지 |
| 🌄 오전반차 (2시 출근) | 2시 | 18시 | 버튼 선택 또는 전자결재 자동 감지 |
| 🌅 오후반차 (8시 출근) | 8시 | 12시 | 버튼 선택 또는 전자결재 자동 감지 |
| 🌅 오후반차 (9시 출근) | 9시 | 14시 | 버튼 선택 또는 전자결재 자동 감지 |
| 🏖️ 연차 | — | — | 버튼 선택 또는 전자결재 자동 감지 |

> ⭐ 실제 클릭 시각: 출근은 1분 일찍(xx:59), 퇴근은 1분 늦게(xx:01) 처리됩니다.

---

## 텔레그램 버튼

**[1단계] 7:30 알림**
```
🕐 오늘 몇시 출근하시나요?
버튼을 누르지 않으면 8시 자동 출근 처리됩니다.

[ 🕗 8시 출근  |  🕘 9시 출근 ]
[ 🌄 오전 반차  |  🌅 오후 반차 ]
[         🏖️ 오늘 연차         ]
```

**[2단계] 오전 반차 선택 시**
```
🌄 오전 반차 - 몇 시 출근하시나요?

[ 🕐 1시 출근 | 🕑 2시 출근 ]
```

**[2단계] 오후 반차 선택 시**
```
🌅 오후 반차 - 몇 시에 출근하시나요?

[ 🕗 8시 출근 | 🕘 9시 출근 ]
```

---

## 전자결재 자동 감지

- 전자결재 내문서함(기안)의 **휴가신청서**를 파싱해 당일 연차/반차를 자동 감지합니다.  
- 감지되면 버튼 없이 자동 설정 메시지만 전송하고 출퇴근을 처리합니다.  
- 같은 문서에 연차와 반차가 섞여 있어도 날짜별로 올바르게 분리합니다.  
- 감지 실패 시에는 버튼 메시지를 정상 발송하며 출퇴근에 영향을 주지 않습니다.

| 휴가 신청 시간 | 감지 결과 |
|---|---|
| 종일 | 연차 |
| 08:xx ~ 12:xx | 오전반차 1시 출근 |
| 09:xx ~ 14:xx | 오전반차 2시 출근 |
| 13:xx ~ 17:xx | 오후반차 8시 출근 |
| 14:xx ~ 18:xx | 오후반차 9시 출근 |

전자결재 날짜 수동 조회: `run-vacation-check.bat`

---

## 필요한 것

- Java 17 이상 ([다운로드](https://www.oracle.com/java/technologies/downloads/#java17))
- Maven 또는 IntelliJ IDEA
- Chrome 브라우저 (ChromeDriver는 자동 설치됨)
- 텔레그램 계정 + 봇 토큰

---

## 설치 방법

### 1. 레포지토리 클론

```bash
git clone https://github.com/minkyungkimm/hiworks-attendance.git
cd hiworks-attendance
```

### 2. 설정 파일 생성

`src/main/resources/config.properties.example`을 복사해서 `config.properties`로 이름을 변경합니다.

```properties
# 하이웍스 계정
hiworks.url=https://login.office.hiworks.com/회사도메인
hiworks.company=회사도메인
hiworks.username=아이디
hiworks.password=비밀번호

# 텔레그램 봇
telegram.bot.token=봇토큰
telegram.chat.id=채팅ID
```

> ⚠️ `config.properties`는 `.gitignore`에 포함되어 깃에 올라가지 않습니다.

### 3. 텔레그램 봇 만들기

1. 텔레그램에서 **@BotFather** 검색 → `/newbot` 입력 → 봇 이름 설정
2. 발급받은 토큰을 `telegram.bot.token`에 입력
3. 봇에게 아무 메시지를 보낸 뒤 아래 URL로 채팅 ID 확인:
   ```
   https://api.telegram.org/bot{봇토큰}/getUpdates
   ```
4. 응답의 `chat.id` 값을 `telegram.chat.id`에 입력

### 4. 빌드

```bash
mvn package -DskipTests
```

IntelliJ를 사용하는 경우 Maven 패널 → `package` 더블클릭

> **IntelliJ 내장 Maven + Java 17 사용 시** (JAVA_HOME 오류 발생하면):
> ```bash
> JAVA_HOME="C:\Program Files\Java\jdk-17" mvn package -DskipTests
> ```

### 5. Windows 작업 스케줄러 등록

PowerShell을 **관리자 권한**으로 열고 아래 명령어 실행:

```powershell
schtasks /create /tn "HiworksAttendance" /tr "\"C:\실제경로\hiworks-attendance\run-scheduler.bat\"" /sc WEEKLY /d MON,TUE,WED,THU,FRI /st 07:25 /f
```

> ⚠️ `C:\실제경로\hiworks-attendance\` 부분을 실제 경로로 변경하세요.  
> 예) `C:\Users\사용자명\IdeaProjects\hiworks-attendance\run-scheduler.bat`

---

## 수동 실행

| 파일 | 역할 |
|---|---|
| `run.bat` | 출근 체크 즉시 실행 |
| `run-checkout.bat` | 퇴근 체크 즉시 실행 |
| `run-halfday-checkout.bat` | 오후 반차 퇴근 즉시 실행 |
| `run-vacation-check.bat` | 전자결재 연차/반차 날짜 조회 |
| `run-scheduler.bat` | 스케줄러 모드 실행 (작업 스케줄러가 사용) |

---

## 로그 & 스크린샷

- 실행 로그: `logs/attendance.log` (날짜별 자동 분리, 30일 보관)
- 스크린샷: `logs/screenshots/` (출근/퇴근 전후 자동 저장, 30일 지난 파일 자동 삭제)

---

## 트러블슈팅

### 출근 시간 잘못 읽는 문제

**증상**: 근무체크 섹션의 현재 시각(`15:33:47`)을 출근 시간으로 잘못 읽음  
**해결**: "출근하기" 텍스트 아래 줄에서만 시간을 탐색, "근무중" 줄은 건너뜀

### 퇴근 여부 판단 오류

**증상**: 이미 퇴근했는데도 퇴근 버튼을 다시 누르려 함  
**해결**: 우측 "근무현황" 섹션에서 퇴근 기록 텍스트 유무로만 판단하도록 변경

### 로딩 중 스크린샷 찍히는 문제

**증상**: 페이지 로딩 중이거나 빈 화면일 때 스크린샷이 저장됨  
**해결**: 스크린샷 전 3단계 대기 추가 (DOM 출현 → 본문 로드 → 시간 텍스트 확인)
