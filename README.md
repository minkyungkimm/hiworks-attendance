# 하이웍스 자동 출퇴근 체크

하이웍스에 로그인해서 출근/퇴근이 안 찍혀있으면 자동으로 체크하는 프로그램입니다.  
텔레그램 봇과 연동해서 매일 7:30에 "오늘 8시 출근하시나요?" 알림을 받고, 버튼 한 번으로 출근 시간을 8시 또는 9시로 설정할 수 있습니다.

## 필요한 것

- Java 17 이상 ([다운로드](https://www.oracle.com/java/technologies/downloads/#java17))
- Maven ([다운로드](https://maven.apache.org/download.cgi)) 또는 IntelliJ IDEA
- Chrome 브라우저
- (선택) 텔레그램 계정 + 봇 토큰

## 사용 방법

### 1. 레포지토리 클론

```bash
git clone https://github.com/minkyungkimm/hiworks-attendance.git
cd hiworks-attendance
```

### 2. 설정 파일 생성

`src/main/resources/config.properties.example` 파일을 복사해서 `config.properties`로 이름을 변경하고, 본인 정보를 입력합니다.

```
src/main/resources/config.properties.example
                    ↓ 복사 후 이름 변경
src/main/resources/config.properties
```

```properties
hiworks.url=https://login.office.hiworks.com/회사도메인
hiworks.company=회사도메인
hiworks.username=본인아이디
hiworks.password=본인비밀번호

# 텔레그램 봇 설정 (선택)
telegram.bot.token=봇토큰
telegram.chat.id=채팅ID
```

> ⚠️ `config.properties`는 `.gitignore`에 포함되어 있어 깃에 올라가지 않습니다.

### 3. 빌드

```bash
mvn package -DskipTests
```

IntelliJ를 사용하는 경우 Maven 패널 → `package` 더블클릭

### 4. 실행

#### 수동 실행

| 파일 | 역할 |
|---|---|
| `run.bat` | 출근 체크 (즉시 실행) |
| `run-checkout.bat` | 퇴근 체크 (오후 6시 이후만 가능) |

더블클릭으로 실행합니다.

#### 자동 스케줄러 모드

```bash
java -jar target/hiworks-attendance-1.0-SNAPSHOT-jar-with-dependencies.jar
```

프로그램을 켜두면 아래 스케줄이 자동으로 실행됩니다:

| 시간 | 동작 |
|---|---|
| 평일 07:30 | 텔레그램으로 "오늘 8시 출근하시나요?" 알림 발송 |
| 평일 08:00 | 출근 체크 (텔레그램에서 "9시"를 선택한 경우 건너뜀) |
| 평일 09:00 | 출근 체크 (텔레그램에서 "9시"를 선택한 경우만 실행) |

## 텔레그램 봇 설정 방법

1. 텔레그램에서 **@BotFather** 검색 → `/newbot` 명령어로 봇 생성
2. 발급받은 **봇 토큰**을 `config.properties`의 `telegram.bot.token`에 입력
3. 봇에게 메시지를 한 번 보낸 뒤 아래 URL로 채팅 ID 확인:
   ```
   https://api.telegram.org/bot{봇토큰}/getUpdates
   ```
4. 응답의 `chat.id` 값을 `telegram.chat.id`에 입력

## 동작 방식

### 텔레그램 자율 출퇴근 흐름

```
07:30  봇: "오늘 8시에 출근하시나요?"  [✅ 네, 8시 출근] [❌ 아니요, 9시]
         │
         ├─ "8시" 클릭  → 08:00 자동 출근
         ├─ "9시" 클릭  → 09:00 자동 출근
         └─ 미응답      → 08:00 자동 출근 (기본값)
```

### 출근 (`run.bat`)

1. 하이웍스 로그인
2. 근무 현황 페이지 이동
3. 이미 출근됨 → 종료
4. 미출근 → 출근 버튼 클릭 후 종료

### 퇴근 (`run-checkout.bat`)

1. 하이웍스 로그인
2. 근무 현황 페이지 이동
3. 이미 퇴근됨 → 종료
4. 출근 기록 없음 → 퇴근 불가, 종료
5. 현재 시각이 **오후 6시 이전** → 퇴근 차단, 종료
6. 오후 6시 이후 + 미퇴근 → 퇴근 버튼 클릭 후 종료

## 로그 & 스크린샷

- 실행 로그: `logs/attendance.log` (날짜별 자동 분리, 30일 보관)
- 오류 발생 시 스크린샷: `logs/screenshots/`
