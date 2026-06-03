# ✈️ MoneyWay: AI 여행 플래너 & 커뮤니티

**MoneyWay**는 AI 기반 여행 추천과 사용자 커뮤니티를 결합한 웹 플랫폼입니다. 예산과 일정만 입력하면 AI가 맞춤 여행 코스를 제안하고, 커뮤니티를 통해 다른 사용자와 정보를 공유할 수 있습니다.

---

## ✨ 주요 기능

- 🤖 **AI 여행 플래너**: 예산, 기간, 스타일을 기반으로 AI가 최적의 일정 추천
- 📝 **사용자 여행 계획**: 장소 선택, 시간표 구성, 직접 예산 조정 가능
- 💰 **실시간 예산 계산기**: 장소 추가 시 실시간 잔여 예산 확인
- 🗣️ **커뮤니티**: 후기, 꿀팁 공유 (게시글/댓글, 좋아요, 스크랩 지원)
- 👤 **사용자 인증**: 카카오 OAuth2 로그인 + JWT 인증
- 🗺️ **관광 정보 제공**: 한국관광공사(TourAPI), 지역 맛집/카페 정보 연동

---

## 🛠️ 기술 스택

| 영역             | 기술                                                   |
|------------------|--------------------------------------------------------|
| Backend          | Java 21, Spring Boot 3.4.5, Spring Security            |
| Database         | MySQL, Redis                                           |
| ORM              | Spring Data JPA (Hibernate)                            |
| 인증             | JWT, OAuth 2.0 (Kakao)                                 |
| 외부 API         | OpenAI API, 한국관광공사 TourAPI                       |
| API 문서화       | Swagger (OpenAPI 3.0)                                  |
| 기타             | Lombok, Docker, Gradle                                 |

---

## 🚀 시작 가이드

### 1. 요구 사항

- Java 21
- Gradle 8.x
- MySQL
- Redis

### 2. 프로젝트 실행

```bash
# 1. 프로젝트 클론
git clone https://github.com/{your-github-username}/MoneyWay1.git
cd MoneyWay1

# 2. 빌드
./gradlew build

# 3. 실행
java -jar build/libs/app.jar
3. 환경 변수 설정
src/main/resources/application.yml → application-local.yml 복사 후 아래 항목 입력:

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/{DB이름}
    username: {DB유저}
    password: {DB비밀번호}

  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: {KAKAO_CLIENT_ID}
            client-secret: {KAKAO_CLIENT_SECRET}
            redirect-uri: http://localhost:8080/login/oauth2/code/kakao

  mail:
    username: {GMAIL_ID}
    password: {GMAIL_APP_PASSWORD}

jwt:
  issuer: {JWT_ISSUER}
  secret_key: {JWT_SECRET}

tour:
  api:
    key: {TOUR_API_KEY}

openai:
  api:
    key: {OPENAI_API_KEY}
📄 API 문서
Swagger UI: http://localhost:8080/swagger-ui.html

📁 주요 패키지 구조
moneyway
├── ai             # AI 여행 코스 추천
├── auth           # 인증 (JWT, OAuth2)
├── cart           # 장바구니 기능
├── common         # 공통 설정 및 예외 처리
├── community      # 커뮤니티 (게시글/댓글)
├── infrastructure # 외부 API 통신
├── place          # 장소 정보 (관광지, 맛집 등)
├── plan           # 여행 계획 및 시간표
└── user           # 사용자 관리
📬 Contact
프로젝트 관련 문의: jjm0203311@naver.com

---
