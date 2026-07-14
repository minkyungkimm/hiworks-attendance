# 하이웍스 자동 출퇴근 체크

하이웍스에 자동으로 출근/퇴근을 체크하는 프로그램입니다.  
텔레그램 봇과 연동해서 매일 7:30에 출근 시간을 선택하고, 출근 시각에 따라 퇴근도 자동으로 처리합니다.

## 전체 동작 흐름

```
07:25  Windows 작업 스케줄러 → 프로그램 자동 시작
07:30  텔레그램 "오늘 8시에 출근하시나요?" 알림
         ├─ ✅ 네, 8시  → 08:00 자동 출근
         ├─ ❌ 아니요, 9시 → 09:00 자동 출근
         └─ 무응답      → 08:00 자동 출근 (기본값)

17:00  출근 시간 확인 → 8:20 이전 출근이면 자동 퇴근
18:00  출근 시간 확인 → 8:20 이후 출근이면 자동 퇴근
18:15  프로그램 자동 종료

※ 공휴일은 텔레그램으로 "오늘은 공휴일" 안내 후 모든 체크 건너뜀
```

## 필요한 것

- Java 17 이상 ([다운로드](https://www.oracle.com/java/technologies/downloads/#java17))
- Maven 또는 IntelliJ IDEA
- Chrome 브라우저
- 텔레그램 계정 + 봇 토큰

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

### 5. Windows 작업 스케줄러 등록

PowerShell을 **관리자 권한**으로 열고 아래 명령어 실행:

```powershell
schtasks /create /tn "HiworksAttendance" /tr "\"C:\경로\hiworks-attendance\run-scheduler.bat\"" /sc WEEKLY /d MON,TUE,WED,THU,FRI /st 07:25 /f
```

등록 후에는 매일 평일 7:25에 프로그램이 자동으로 시작됩니다.

## 수동 실행

자동 스케줄러 없이 즉시 실행하고 싶을 때 사용합니다.

| 파일 | 역할 |
|---|---|
| `run.bat` | 출근 체크 즉시 실행 |
| `run-checkout.bat` | 퇴근 체크 즉시 실행 |
| `run-scheduler.bat` | 스케줄러 모드로 실행 (작업 스케줄러가 사용) |

## 퇴근 시간 기준

출근 시간을 페이지에서 자동으로 읽어 퇴근 시간을 결정합니다.

| 출근 시간 | 자동 퇴근 시간 |
|---|---|
| 8:20 이전 | 오후 5시 (17:00) |
| 8:20 이후 | 오후 6시 (18:00) |

## 공휴일 처리

별도 설정 없이 자동으로 한국 공휴일을 감지합니다.  
공휴일에는 텔레그램으로 "🎉 오늘은 공휴일입니다" 알림을 보내고 출퇴근 체크를 건너뜁니다.

## 로그 & 스크린샷

- 실행 로그: `logs/attendance.log` (날짜별 자동 분리, 30일 보관)
- 오류 발생 시 스크린샷: `logs/screenshots/`
