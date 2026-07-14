# 하이웍스 자동 출석체크

하이웍스에 로그인해서 출근이 안 찍혀있으면 자동으로 출근 체크하는 프로그램입니다.

## 필요한 것

- Java 17 이상 ([다운로드](https://www.oracle.com/java/technologies/downloads/#java17))
- Maven ([다운로드](https://maven.apache.org/download.cgi)) 또는 IntelliJ IDEA
- Chrome 브라우저

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
```

> ⚠️ `config.properties`는 `.gitignore`에 포함되어 있어 깃에 올라가지 않습니다.

### 3. 빌드

```bash
mvn package -DskipTests
```

IntelliJ를 사용하는 경우 Maven 패널 → `package` 더블클릭

### 4. 실행

#### 수동 실행 (더블클릭)
`run.bat` 파일을 더블클릭합니다.

#### 명령어로 실행
```bash
java -jar target/hiworks-attendance-1.0-SNAPSHOT-jar-with-dependencies.jar --now
```

## 동작 방식

1. 하이웍스 로그인
2. 근무 현황 페이지 이동
3. 이미 출근이 찍혀있으면 → 종료
4. 출근이 안 찍혀있으면 → 출근 버튼 클릭 후 종료

## 로그 & 스크린샷

실행 로그는 `logs/attendance.log`에 저장됩니다.
오류 발생 시 `logs/screenshots/` 폴더에 스크린샷이 저장됩니다.
