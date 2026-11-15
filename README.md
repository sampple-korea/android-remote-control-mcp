<p align="center">
  <img src="docs/assets/arc-mcp-logo.png" alt="ARC MCP, Android Remote Control MCP" width="640">
</p>

# Android Remote Control MCP

[![CI](https://github.com/danielealbano/android-remote-control-mcp/actions/workflows/ci.yml/badge.svg)](https://github.com/danielealbano/android-remote-control-mcp/actions/workflows/ci.yml)
[![License: GPL v3 or later](https://img.shields.io/badge/License-GPLv3%2B-blue.svg)](LICENSE.md)
[![Tunnel status](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fstatus.arc-tunnel.io%2Findex.json&query=%24.data.attributes.aggregate_state&label=tunnel&color=blue)](https://status.arc-tunnel.io/)

**MCP(Model Context Protocol) 서버**로 동작하는 Android 애플리케이션입니다. 접근성 서비스와 스크린샷 캡처를 이용해 AI 모델이 원격에서 **Android 기기를 완전히 제어**할 수 있게 해 줍니다.

앱은 Android 기기(또는 에뮬레이터)에서 직접 실행되며, MCP 프로토콜을 구현한 HTTP 서버(선택적으로 HTTPS)를 제공합니다. Claude 같은 AI 모델이 여기에 연결해 기기의 모든 앱과 상호작용할 수 있습니다. UI 요소 읽기, 버튼 탭, 텍스트 입력, 스와이프, 스크린샷 캡처, 파일 관리, 앱 실행 등이 가능합니다.

선택 기능인 [프라이버시 모드](#프라이버시-모드)는 도구가 반환하는 내용에서 이메일, 카드 번호, 자격 증명, 이름 같은 개인정보를 탐지해, 기기를 떠나 AI 제공업체로 전달되기 전에 가립니다. 탐지는 전부 기기 안에서 이루어집니다. 노출을 줄여 주지만, 개인정보가 전혀 새어 나가지 않는다고 보장하지는 못합니다.

어디서든 휴대전화에 접속할 수 있도록, 무료 종단 간 암호화 HTTPS 터널이 계정이나 가입 없이 공개 URL을 제공합니다. TLS가 휴대전화에서 종료되므로 터널 서비스는 암호화된 바이트만 중계할 뿐 트래픽을 읽을 수 없습니다. Cloudflare와 ngrok도 지원하지만, 이들은 TLS를 직접 종료하므로 트래픽을 읽을 수 있습니다.

> **경고:** 이 소프트웨어는 어떠한 종류의 보증도 없이 "있는 그대로" 제공되며, **연구 및 교육 목적으로만** 제공됩니다. 저작자는 이 도구를 불법적이거나 승인되지 않았거나 비윤리적인 활동에 사용하는 것을 용인하지 않습니다. 관련 법률과 규정을 준수해 사용할 책임은 전적으로 사용자에게 있습니다. 이 소프트웨어를 사용함으로써 사용자는 책임감 있게, 그리고 본인의 책임하에 사용하는 데 동의하는 것입니다.

> **경고:** 무료 E2E HTTPS 터널은 이 저장소의 [Releases](https://github.com/danielealbano/android-remote-control-mcp/releases)에서 제공하는 서명된 빌드에서만 동작합니다. 그 외의 빌드는 보안상의 이유로 거부됩니다.

---

## 목차

- [데모](#데모)
- [기능](#기능)
- [설치](#설치)
- [설정](#설정)
- [프라이버시 모드](#프라이버시-모드)
- [연동](#연동)
- [구성](#구성)
- [권한 참조](#권한-참조)
- [보안](#보안)
- [기여하기](#기여하기)
- [라이선스](#라이선스)

---

## 데모

<table>
  <tr>
    <td align="center" width="33%" valign="top">
      <a href="docs/assets/demo-reddit-post.gif" title="클릭하면 원본 해상도로 볼 수 있습니다"><img src="docs/assets/demo-reddit-post-small.gif" alt="MCP 서버를 통해 Reddit에 글 올리기" width="240"></a>
      <br><em>Reddit에 글 올리기 (배속 재생, 원본 약 6분)</em>
    </td>
    <td align="center" width="33%" valign="top">
      <a href="docs/assets/demo-install-booking.gif" title="클릭하면 원본 해상도로 볼 수 있습니다"><img src="docs/assets/demo-install-booking-small.gif" alt="MCP 서버를 통해 booking.com 앱 설치하기" width="240"></a>
      <br><em>booking.com 앱 설치하기</em>
    </td>
    <td align="center" width="33%" valign="top">
      <a href="docs/assets/demo-flight-search.gif" title="클릭하면 원본 해상도로 볼 수 있습니다"><img src="docs/assets/demo-flight-search-small.gif" alt="MCP 서버를 통해 Skyscanner에서 취리히발 이스탄불행 항공편 검색하기" width="240"></a>
      <br><em>Skyscanner 항공편 검색, 취리히 &rarr; 이스탄불 (배속 재생, 원본 약 4분)</em>
    </td>
  </tr>
</table>

<p align="center"><em>AI 모델이 MCP 서버를 통해 Android 기기를 제어하는 모습입니다 &mdash; 영상을 클릭하면 원본 해상도로 볼 수 있습니다.</em></p>

## 기능

### MCP 서버
- Android에서 직접 실행되는 HTTP 서버 (Ktor + Netty), 선택적으로 HTTPS 지원
- `/mcp`의 Streamable HTTP 전송 (MCP 사양 준수, JSON 전용, SSE 없음)
- 결합 인증: 정적 Bearer 토큰 및/또는 자체 내장 OAuth 2.1 서버 (Claude.ai / Claude Desktop 커스텀 커넥터, 기기에서 직접 승인)
- 자동 생성되는 자체 서명 TLS 인증서 (또는 사용자 지정 인증서 업로드)
- 바인딩 주소 설정 가능: 로컬호스트(127.0.0.1), 네트워크(0.0.0.0) 또는 기기의 특정 IP 주소
- 부팅 시 자동 시작
- 무료 종단 간 암호화 터널(계정 불필요), Cloudflare Quick Tunnels 또는 ngrok을 통한 원격 접근 터널 (공개 HTTPS URL)

### 15개 카테고리, 65개 MCP 도구

화면 조회, 시스템 동작, 터치 동작, 제스처, 노드 동작, 텍스트 입력, 유틸리티, 파일 작업, 앱 관리, 카메라, 인텐트, 알림, 위치, 공유, 셸.

모든 도구 이름에는 기본적으로 `android_` 접두사가 붙습니다(예: `android_tap`). 기기 슬러그를 설정하면(예: `pixel7`) 접두사가 `android_pixel7_`이 됩니다(예: `android_pixel7_tap`).

입출력 스키마와 예시를 포함한 전체 도구 레퍼런스는 [docs/MCP_TOOLS.md](docs/MCP_TOOLS.md)를 참고하세요.

### Android 앱
- 탭 레이아웃(서버 / 설정 / 정보)과 다크 모드를 갖춘 Material Design 3 UI
- 서버 상태 모니터링(실행 중/중지됨) 및 권한 경고 배너
- 연결 정보 표시 (IP, 포트, 토큰, 터널 URL)
- 도구별·매개변수별 권한 (개별 MCP 도구 사용 설정/해제)
- 권한 관리 (접근성, 알림, 카메라, 마이크)
- 원격 접근 터널 구성 (무료 E2E 터널 / Cloudflare / ngrok)
- 저장 위치 관리 (자동 위치 + 파일 도구를 위한 SAF 권한 부여)
- 서버 로그 뷰어 (MCP 도구 호출, 터널 이벤트)
- ADB를 통한 헤드리스 설정 (UI 없이 구성, 권한 부여, 서버 시작/중지)

### 대안과의 비교

| 기능 | 이 프로젝트 | [mobile-mcp] | [Android-MCP] | [android-mcp-server] | [adb-mcp] | [droidrun-mcp] |
|---------|:-:|:-:|:-:|:-:|:-:|:-:|
| MCP 도구 수 | 65 | 21 | 11 | 5 | 10 | 11 |
| 휴대전화에서 실행 (ADB 불필요) | :white_check_mark: | :x: | :x: | :x: | :x: | :x: |
| 동작 지연 시간 | 10-100 ms | 1-4 s | 1-4 s | 1-4 s | 1-4 s | 1-4 s |
| 인터넷을 통한 사용 | :white_check_mark: | :x: | :x: | :x: | :x: | :x: |
| 토큰 효율적인 화면 상태 | :white_check_mark: | :x: | :white_check_mark: | :x: | :x: | :white_check_mark: |
| 주석이 달린 스크린샷 | :white_check_mark: | :x: | :white_check_mark: | :x: | :x: | :x: |
| 스크린샷 해상도/품질 설정 | :white_check_mark: | :x: | :x: | :x: | :x: | :x: |
| 도구별 사용 설정/해제 | :white_check_mark: | :x: | :x: | :x: | :x: | :x: |
| 다중 기기 지원 | :white_check_mark: | :white_check_mark: | :x: | :x: | :x: | :x: |
| 카메라, 클립보드, 파일, 다운로드 | :white_check_mark: | :x: | :x: | :x: | :x: | :x: |
| iOS 지원 | :x: | :white_check_mark: | :x: | :x: | :x: | :x: |

[mobile-mcp]: https://github.com/mobile-next/mobile-mcp
[Android-MCP]: https://github.com/CursorTouch/Android-MCP
[android-mcp-server]: https://github.com/minhalvp/android-mcp-server
[adb-mcp]: https://github.com/srmorete/adb-mcp
[droidrun-mcp]: https://github.com/chukfinley/droidrun-mcp-server

대부분의 대안은 호스트 컴퓨터에서 실행되는 ADB에 의존합니다. 즉 USB 케이블이나 로컬 네트워크 연결, 그리고 휴대전화 옆에 놓인 컴퓨터가 필요합니다. 이 프로젝트는 전부 기기 자체에서 실행되므로, 터널을 통해 MCP 엔드포인트를 노출하고 어디서든 휴대전화를 제어할 수 있습니다.

토큰 효율 측면에서, ADB 기반 도구는 보통 `uiautomator`의 원시 XML 덤프를 반환하는데, 이는 여기서 사용하는 간결한 표현보다 10~50배 더 장황해지기 쉽습니다. 번호가 매겨진 스크린샷 주석, 설정 가능한 이미지 품질, 필요 없는 도구를 끌 수 있는 기능(모든 도구 정의는 매 턴마다 토큰을 소비합니다)까지 더해져, 에이전트 루프에서 상호작용당 비용을 크게 줄여 줍니다.

---

## 설치

### 어떤 빌드를 받아야 할까: GMS 또는 FOSS

각 릴리스는 두 가지 버전의 APK를 제공합니다. 기기에 맞는 것을 고르세요.

- **GMS** (`…-gms-release.apk`) — 전체 기능 빌드입니다. 휴대전화에 **Google 모바일 서비스(Google Play 서비스)가 설치되어 있어야 합니다.** Google 서비스가 기본 탑재된 일반적인 기기/에뮬레이터에서는 이 빌드를 선택하세요.
- **FOSS** (`…-foss-release.apk`) — **Google 모바일 서비스에 의존하지 않는** Google-free 빌드입니다. Google이 제거된 ROM이나 Google 서비스를 사용할 수 없는 기기를 포함해 **모든** Android 휴대전화에서 동작합니다.

잘 모르겠다면, Play 스토어가 있는 일반적인 휴대전화에서는 GMS 빌드가 맞습니다.

### 방법 A: 휴대전화에서 다운로드 (가장 쉬움)

1. 휴대전화 브라우저에서 [Releases](https://github.com/danielealbano/android-remote-control-mcp/releases) 페이지를 엽니다
2. 최신 릴리스에서 APK를 다운로드합니다
3. 다운로드한 APK를 열고 안내에 따라 설치합니다 (출처를 알 수 없는 앱 설치를 허용해야 할 수 있습니다)

### 방법 B: PC에서 다운로드하여 ADB로 설치

1. [Releases](https://github.com/danielealbano/android-remote-control-mcp/releases) 페이지에서 APK를 다운로드합니다
2. USB로 휴대전화를 연결합니다 (USB 디버깅 사용 설정 필요)
3. APK를 설치합니다 (다운로드한 GMS 또는 FOSS 파일 사용):
```bash
adb install android-remote-control-mcp-<version>-gms-release.apk
```

### 방법 C: 소스에서 빌드

```bash
git clone https://github.com/danielealbano/android-remote-control-mcp.git
cd android-remote-control-mcp
make build
make install  # 연결된 기기/에뮬레이터에 설치합니다
```

전체 빌드 요구 사항과 안내는 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

---

## 설정

1. **앱을 엽니다.** 서버 탭에 권한 경고 배너가 표시됩니다. 거기서 바로 권한을 부여하거나 설정 > 권한에서 부여하세요.

2. **권한을 부여합니다:**

   - **접근성 서비스** — **필수.** UI 조회, 동작 실행, 스크린샷을 담당하며, 이 권한 없이는 아무것도 동작하지 않습니다.
     - **토글이 회색으로 비활성화되어 있다면** (Android 13 이상, APK로 설치한 앱에서 Samsung/One UI에 흔함): Android의 "제한된 설정" 보호 기능 때문입니다. *"제한된 설정 — 보안을 위해 현재 이 설정을 사용할 수 없습니다."* 라는 대화상자가 표시됩니다. 차단을 해제하려면 **설정 → 애플리케이션 → Android Remote Control MCP → 앱 정보**로 이동해 **⋮ 메뉴**(오른쪽 위)를 탭하고 **제한된 설정 허용**을 선택한 다음, PIN/패턴/생체 인증으로 확인한 뒤 접근성 서비스를 사용 설정하세요. (Play 스토어 / F-Droid로 설치한 경우에는 해당하지 않습니다.)
   - **알림** — **선택 사항** (OAuth 사용 시 권장). OAuth 로그인 승인 요청(Claude.ai / Claude Desktop, ChatGPT 또는 서드파티 OAuth 클라이언트)이 헤드업 알림으로 도착하며, 이를 탭해 2자리 코드를 확인하고 승인합니다. 다만 OAuth 로그인에 **더 이상 필수는 아닙니다.** 이 권한을 부여하지 않아도, 대기 중인 요청이 **앱의 서버 화면에 카드**로 표시되어 탭해서 검토하고 승인/거부할 수 있습니다. **이벤트 채널** 푸시 기능(예: 기기 이벤트를 Claude Code로 푸시)은 포그라운드 서비스로 실행되며, Android 13 이상에서는 이 권한을 통해 상태 알림을 게시합니다.
   - **기타 권한** (선택 사항) — 카메라, 마이크, 위치 권한은 각각에 해당하는 MCP 도구를 사용할 수 있게 합니다. 이 권한들이 없어도 앱은 동작하며, 권한을 부여하기 전까지 해당 기능만 비활성화됩니다. 전체 목록은 아래의 [권한 참조](#권한-참조)를 참고하세요.
   - **저장 위치** — 파일 도구를 사용할 계획이라면 설정 > 저장소에서 구성하세요 (다운로드 같은 자동 위치와 SAF를 통한 사용자 지정 위치).

3. **서버를 시작합니다.** **서버 탭**으로 돌아가 **시작**을 탭하세요.

서버는 기본적으로 `http://127.0.0.1:8080`에서 시작됩니다. 연결 정보(IP, 포트, 토큰, URL)는 서버 탭에 표시됩니다.

> **참고**: `127.0.0.1`은 컴퓨터가 아니라 휴대전화의 로컬호스트를 가리킵니다. 컴퓨터에서 연결하려면 [adb 포트 포워딩](#adb-포트-포워딩으로-사용하기)을 사용하거나, `0.0.0.0`(네트워크 모드)에 바인딩하거나, [원격 접근 터널](#원격-접근-터널-사용하기)을 사용 설정하세요.

---

## 프라이버시 모드

프라이버시 모드는 MCP 도구가 반환하는 모든 내용(화면 콘텐츠, 알림, 클립보드, 파일 등)에서
개인정보 — 이메일, 신용카드 및 IBAN, 자격 증명, 전화번호, 주민등록번호 등 신분증 번호, 주소,
이름 — 를 탐지해, **기기를 떠나** AI 제공업체로 전달되기 **전에** 가립니다. 탐지는 전부 기기에서
실행됩니다. 빠른 규칙 기반 검사(체크섬, 형식, 필드 문맥)와 로컬 NER 모델을 결합하며, 클라우드
호출은 없습니다. 탐지된 값은 일관된 자리표시자로 가명 처리되거나(`EMAIL#a1b2c`, 화면이 바뀌어도
AI 도구가 계속 동작함) 완전히 가려집니다. 탐지는 최선을 다하는 완화 수단일 뿐 보장이 아닙니다.

셸 도구에는 적용되지 않습니다. 명령과 그 출력은 변경 없이 그대로 전달됩니다.

모델은 [Ai4Privacy 다국어 익명화 모델](https://huggingface.co/ai4privacy/llama-ai4privacy-multilingual-categorical-anonymiser-openpii)
(MIT 라이선스, Built with Llama)이며, 처음 사용 설정할 때 고정된 Hugging Face 리비전에서 직접
한 번만 다운로드되고(약 150 MB) 기기에서 체크섬으로 검증됩니다.

### 측정된 탐지율

전체 파이프라인이 카테고리별로 탐지한 개인정보 항목의 비율로, 8개 언어의 실제 UI와 유사한 콘텐츠를
대상으로 저장소에 포함된 효과성 벤치마크(`make privacy-benchmark`)로 측정했습니다.

| 카테고리 | 탐지율 |
|---|---|
| 주소 | 77.9% |
| 카드 및 IBAN | 75.3% |
| 자격 증명 | 90.9% |
| 이메일 | 100.0% |
| 이름 | 0.8% |
| 신분증 번호 | 93.0% |
| 전화번호 | 84.4% |

이름은 온디바이스 모델의 알려진 약점입니다(벤치마크에 포함된 전 세계의 다양한 이름보다 흔한 서구권
이름을 훨씬 잘 인식합니다). 파인튜닝한 모델로 이름 탐지를 개선하는 작업이 로드맵에 있습니다. 실제
결과는 콘텐츠와 언어에 따라 달라집니다.

### 프라이버시 모드 사용 설정

- **메인 서버 화면에서**: 프라이버시 모드 카드(모드가 꺼져 있는 동안 표시됨)의 **프라이버시 모드
  설정**을 탭합니다.
- **또는 설정 → 프라이버시 모드에서**: **프라이버시 모드 사용**을 켜고 일회성 모델 다운로드를
  확인합니다. 다운로드와 준비가 끝나면 짧은 온디바이스 벤치마크가 화면당 예상 추가 시간을 측정해
  같은 화면에 표시합니다.
- 보호되는 카테고리, 가명 처리/완전히 가리기, 자리표시자 형식은 **설정 → 프라이버시 모드**에서
  설정할 수 있습니다.

---

## 연동

### Claude Desktop / Claude Code

`.mcp.json` 구성 파일에 서버를 추가합니다.

```json
{
  "mcpServers": {
    "android-phone": {
      "type": "http",
      "url": "http://DEVICE_IP:PORT/mcp",
      "headers": {
        "Authorization": "Bearer YOUR_TOKEN"
      }
    }
  }
}
```

`DEVICE_IP`, `PORT`, `YOUR_TOKEN`을 앱의 서버 탭에 표시된 값으로 바꾸세요. 서버가 로컬호스트에 바인딩되어 있다면(기본값) 연결하려면 [adb 포트 포워딩](#adb-포트-포워딩으로-사용하기) 또는 [원격 접근 터널](#원격-접근-터널-사용하기)이 필요합니다.

> **커스텀 헤더를 지원하지 않는 클라이언트를 위한 참고:** MCP 클라이언트가 `Authorization` 헤더를 보낼 수 없고 OAuth도 지원하지 않는다면, 앱에서 두 인증 방식을 모두 끌 수 있습니다(**설정 → 접근** — Bearer 토큰과 OAuth 사용 중지). 이 경우 서버가 **개방** 상태가 되어 접근할 수 있는 사람은 누구나 완전히 제어할 수 있으므로, 앱이 경고하고 확인을 요청합니다. 신뢰하는 네트워크에서만 이렇게 하세요.

### Claude.ai 및 Claude Desktop에서 연결 (커스텀 커넥터, OAuth)

Claude.ai(웹)와 Claude Desktop은 OAuth 2.1을 사용하는 **커스텀 커넥터**(원격 MCP)로 연결합니다. 앱 자체가 OAuth 인증 서버이므로 외부 계정이나 사전 등록이 필요 없습니다. 이를 위해서는 서버가 **공개 HTTPS URL**로 접근 가능해야 하므로, 먼저 [원격 접근 터널](#원격-접근-터널-사용하기)을 사용 설정해야 합니다. `localhost`/LAN 주소나 `adb` 포트 포워딩으로는 동작하지 **않습니다.**

1. **OAuth는 기본적으로 사용 설정되어 있습니다** — 별도 조치가 필요 없습니다(이전에 사용 중지했다면 **설정 → 접근**에서 다시 사용 설정하세요). Bearer 토큰도 계속 사용 설정되어 있으며, 둘 다 허용됩니다.
2. **설정 → 터널**을 열고 **원격 접근 사용**을 켠 다음(기본값인 무료 E2E 터널은 계정이 필요 없으며, 첫 설정에 약 10~30초가 걸립니다) 서버를 시작합니다. 서버 탭에서 공개 `https://…` URL을 복사하고 뒤에 `/mcp`를 붙입니다(예: `https://<name>.d.arc-tunnel.io/mcp`).
3. Claude에서 **[Customize → Connectors → Add custom connector](https://claude.ai/customize/connectors?modal=add-custom-connector)** 메뉴를 열고 `https://…/mcp` URL을 붙여 넣은 다음, **OAuth Client ID와 Client Secret은 비워 둡니다**(앱이 동적 클라이언트 등록을 사용합니다). **Add**를 클릭합니다.
4. Claude가 **2자리 코드**를 표시하는 브라우저 승인 페이지를 엽니다. 기기에서 헤드업 알림을 탭하거나 — 알림 권한이 꺼져 있다면 앱의 **서버** 화면을 열어 **대기 중인 연결 승인** 카드를 탭해 — 승인 화면을 열고, 코드가 일치하는지 확인한 뒤 **승인**합니다.
5. 연결된 클라이언트는 **설정 → 접근 → 연결된 클라이언트**에서 언제든지 관리하거나 권한을 취소할 수 있습니다. 권한을 취소하면 해당 클라이언트의 토큰이 즉시 무효화됩니다.

> **공개 URL 재정의 (선택 사항):** 터널/호스트 구성상 고정된 공개 호스트가 필요하다면(또는 신뢰할 수 있는 프록시 없이 `0.0.0.0`에 바인딩한다면), 설정 → 접근에서 **공개 URL 재정의**를 설정해 OAuth 메타데이터와 링크가 안정적인 호스트를 사용하게 하세요.

커스텀 커넥터는 Free(커넥터 1개), Pro, Max, Team, Enterprise 플랜에서 사용할 수 있습니다(현재 베타).

> ⚠️ **보안:** 공개 터널 URL은 민감한 정보로 취급하고, 본인이 시작한 연결만 승인하며(2자리 코드 확인), 더 이상 사용하지 않는 클라이언트는 권한을 취소하고, 작업이 끝나면 터널과 서버를 중지하세요. 민감한 데이터가 들어 있는 기기에는 절대 연결하지 마세요.

### ChatGPT에서 연결 (커스텀 커넥터, OAuth)

ChatGPT는 동일한 자체 내장 OAuth 2.1 흐름을 사용하는 **커스텀 MCP 커넥터**로 서버에 연결합니다. Claude와 마찬가지로 서버가 **공개 HTTPS URL**로 접근 가능해야 하므로, 먼저 [원격 접근 터널](#원격-접근-터널-사용하기)을 사용 설정하세요. `localhost`/LAN 주소나 `adb` 포트 포워딩으로는 동작하지 **않습니다.** 커스텀 커넥터는 유료 플랜(Plus, Pro, Business, Enterprise 또는 Edu — Free 제외)이 필요하며 **ChatGPT 웹 앱**에서 설정합니다.

1. **개발자 모드 사용 설정** — 웹의 ChatGPT에서 **Settings → Connectors → Advanced settings**(일부 빌드에서는 **Apps & Connectors**로 표시됨)를 열고 **Developer mode**를 켭니다.
2. **터널 시작** — 위의 Claude 흐름과 동일합니다. OAuth는 기본적으로 사용 설정된 상태로 두고, 설정 → 터널에서 **원격 접근 사용**을 켠 다음 서버를 시작하고, 서버 탭에서 공개 `https://…/mcp` URL을 복사합니다.
3. **커넥터 추가** — **Settings → Connectors → Create**를 열고 이름과 설명을 입력한 뒤 `https://…/mcp` URL을 붙여 넣고, 인증 방식으로 **OAuth**를 선택하며, Client ID / Client Secret은 비워 둡니다(앱이 동적 클라이언트 등록으로 클라이언트를 자체 등록합니다).
4. **기기에서 승인** — ChatGPT가 OAuth 흐름을 시작합니다. 헤드업 알림을 탭하거나 — 알림 권한이 꺼져 있다면 앱의 **서버** 화면을 열어 **대기 중인 연결 승인** 카드를 탭해 — 2자리 코드가 일치하는지 확인하고 **승인**합니다(Claude 승인 단계와 동일).
5. 연결된 클라이언트는 앱의 **설정 → 접근 → 연결된 클라이언트**에서 언제든지 관리하거나 권한을 취소할 수 있습니다.

> ⚠️ **보안:** 동일한 주의 사항이 적용됩니다. 터널 URL은 민감한 정보로 취급하고, 본인이 시작한 연결만 승인하며(코드 확인), 사용하지 않는 클라이언트는 권한을 취소하고, 작업이 끝나면 터널과 서버를 중지하세요.

### 기타 MCP 클라이언트

MCP 서버는 결합 인증(정적 Bearer 토큰 및/또는 OAuth 2.1)을 사용하는 표준 Streamable HTTP 엔드포인트를 `/mcp`에 제공합니다. MCP와 호환되는 클라이언트라면 무엇이든 연결할 수 있습니다. 구체적인 구성 형식은 사용하는 클라이언트의 문서를 참고하세요.

### MCP Inspector로 테스트

[MCP Inspector](https://github.com/modelcontextprotocol/inspector)는 MCP 서버용 공식 시각적 테스트 도구입니다. 브라우저 기반 UI로 연결하고, 도구 목록을 보고, 매개변수를 입력하고, 응답을 확인할 수 있습니다.

```bash
npx @modelcontextprotocol/inspector
```

`http://localhost:6274`에 UI가 열리며, 여기서 서버에 연결해 도구를 대화형으로 테스트할 수 있습니다.

### curl로 테스트

모든 요청은 `POST /mcp`를 통해 JSON-RPC 2.0으로 전송됩니다(Streamable HTTP 전송). 서버는 상태를 유지하지 않습니다(stateless). 모든 요청이 독립적이므로 열어야 할 세션도, 보내야 할 `mcp-session-id`도 없으며 도구를 바로 호출할 수 있습니다.

```bash
# 사용 가능한 도구 목록 조회
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# 현재 화면 상태 가져오기 (UI 노드 + 선택적 스크린샷)
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"android_get_screen_state","arguments":{}}}'

# 좌표를 탭
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"android_tap","arguments":{"x":540,"y":1200}}}'
```

Bearer 토큰은 앱의 연결 정보에 표시되며 바로 복사할 수 있습니다. Bearer 인증은 (값을 지우는 것이 아니라) **설정 → 접근**의 **Bearer 토큰 인증** 토글로 제어됩니다. 사용 설정된 상태에서는 모든 `/mcp` 요청이 토큰(또는 유효한 OAuth 액세스 토큰)을 제시해야 합니다. 서버는 Bearer 토큰과 OAuth가 **둘 다** 사용 중지된 경우에만 인증되지 않은 요청을 받아들입니다.

---

## 구성

### 서버 설정 (앱 UI)

| 설정 | 기본값 | 설명 |
|---------|---------|-------------|
| 포트 | `8080` | HTTP/HTTPS 서버 포트 |
| 바인딩 주소 | `127.0.0.1` | `127.0.0.1`(로컬호스트, adb 포트 포워딩과 함께 사용), `0.0.0.0`(네트워크, 모든 인터페이스) 또는 기기의 특정 IP 주소(IPv4 또는 IPv6. IPv6 링크 로컬은 불가, `%3` 같은 존 인덱스 없이 입력. 기기에 해당 주소가 없는 동안에는 서버가 시작되지 않음) |
| Bearer 토큰 | 사용, 자동 생성된 UUID | MCP 요청용 정적 토큰(설정 → 접근). 적용 여부는 값을 지우는 것이 아니라 Bearer 토글로 정해집니다. |
| OAuth | 사용 | Claude.ai / Claude Desktop 커스텀 커넥터를 위한 자체 내장 OAuth 2.1 서버(설정 → 접근). |
| 공개 URL 재정의 | 비어 있음(자동 감지) | OAuth 메타데이터와 공유 링크에 사용되는 공개 호스트를 고정합니다. |
| HTTPS | 사용 안 함 | 자동 생성된 자체 서명 인증서(호스트 이름 설정 가능)로 HTTPS를 사용하거나 사용자 지정 .p12/.pfx를 업로드합니다 |
| 부팅 시 자동 시작 | 사용 안 함 | 기기가 부팅되면 MCP 서버를 자동으로 시작합니다 |
| 기기 슬러그 | 비어 있음 | 도구 이름 접두사에 쓰이는 선택적 기기 식별자(예: `pixel7`이면 도구가 `android_pixel7_tap`이 됨) |
| 원격 접근 터널 | 사용 안 함 | 공개 HTTPS URL로 서버를 노출합니다(기본값은 무료 E2E 터널, Cloudflare Quick Tunnels 또는 ngrok) |
| 터널 로컬 포트 | 이 설정이 생기기 전에 설치된 경우 서버 포트(HTTPS 때문에 Cloudflare/ngrok 터널이 꺼져 있던 경우 제외), 그 외에는 61000-65535 범위의 무작위 포트 | Cloudflare와 ngrok이 전달하는 포트(`http://localhost:<port>`). 서버가 `127.0.0.1`의 같은 포트에서 일반 HTTP를 제공하면(로컬호스트, 네트워크 또는 특정 IP 127.0.0.1) 터널이 그 포트를 사용하고, 그렇지 않으면 서버가 같은 인증을 적용한 일반 HTTP 리스너를 `127.0.0.1:<port>`에 별도로 엽니다. HTTPS를 켜고 이런 바인딩을 사용한다면 서버 포트와 다른 포트를 선택하세요 |
| 도구 권한 | 모두 사용 | 도구별·매개변수별 사용 설정/해제(설정 > MCP 도구) |
| 파일 크기 제한 | 50 MB | 파일 작업의 최대 파일 크기(범위 1-500 MB) |
| HTTP 다운로드 허용 | 사용 안 함 | `android_download_from_url`로 HTTPS가 아닌 다운로드를 허용합니다 |
| 검증되지 않은 HTTPS 허용 | 사용 안 함 | `android_download_from_url`에서 검증되지 않은(자체 서명되었거나 유효하지 않은) HTTPS 인증서를 수락합니다(보안 수준 낮음) |
| 로컬 네트워크 다운로드 차단 | 사용 안 함 | `android_download_from_url`이 이 휴대전화, 로컬 네트워크 또는 VPN 주소(루프백, 미지정, 사설 LAN, 링크 로컬, CGNAT/Tailscale 및 IPv6 고유 로컬 주소, 그리고 이들의 IPv4 매핑/호환 IPv6 형식 포함)로의 연결을 거부합니다 |
| 다운로드 제한 시간 | 60초 | 파일 다운로드 제한 시간(범위 10-300초) |
| 셸: 권한 상승 명령 허용 | 사용 안 함 | MCP 클라이언트가 Shizuku(adb shell 사용자로, Shizuku가 root로 실행 중이면 root로) 또는 `su`(root로)를 통해 단일 셸 명령 실행을 요청할 수 있게 합니다. 모든 셸 설정은 설정 → 셸에 있으며 서버가 실행 중일 때도 즉시 적용됩니다 |
| 셸: 권한 상승 요청 자동 허용 | 사용 안 함 | 모든 권한 상승 명령을 묻지 않고 실행합니다(차단 목록은 여전히 적용됨) |
| 셸: 차단 목록 | 비어 있음 | 패턴과 일치하는 명령은 권한 상승 여부와 관계없이 항상 거부됩니다(패턴은 명령 전체와 일치해야 하며 `*`는 모든 문자열과 일치: `*reboot*`). 텍스트만 비교하므로 같은 동작을 다른 방식으로 작성하면 거부되지 않습니다 |
| 셸: 허용 목록 | 비어 있음 | 자동 허용이 꺼져 있을 때 패턴과 일치하는 권한 상승 명령은 묻지 않고 실행됩니다(패턴은 명령 전체와 일치해야 하며, `*`는 `;`, `&`, `\|`, `` ` ``, `$`, `(`, `)`, `<`, `>` 및 줄바꿈과는 일치하지 않음). 그 밖의 권한 상승 명령은 휴대전화에서 승인을 요청합니다. 명령과 함께 전송된 환경 변수(`envs`)는 검사하지 않습니다 |
| 셸: 최대 실행 프로세스 수 | 16 | 범위 1-64. 기기에서 32개를 넘게 실행되면 Android가 앱의 자식 프로세스를 중지할 수 있습니다. 이렇게 중지된 일반 명령은 다른 알림 없이 종료 코드 137로 끝납니다. |
| 셸: 보관할 종료 프로세스 수 | 32 | 범위 0-256 |
| 셸: 백그라운드 제한 시간, 기본값 | 30분 | 1분부터 최댓값까지 |
| 셸: 백그라운드 제한 시간, 최댓값 | 120분 | 범위 1-1440분, 기본값보다 작을 수 없음 |
| 셸: 승인 제한 시간 | 60초 | 범위 10-85초 |
| 셸: 포그라운드 대기 제한 | 90초 | 범위 10-90초. `android_shell_exec`는 최대 이 시간만큼 기다리고, 이후 명령은 백그라운드에서 계속됩니다 |
| 셸: 호출당 출력 | 64 KiB | 범위 4-256 KiB |
| 셸: 프로세스당 보관 출력 | 1024 KiB | 범위 64-16384 KiB. 이를 넘으면 출력의 처음과 끝만 보관됩니다. 실행 중인 프로세스와 보관된 종료 프로세스 각각에 대해 앱의 메모리에 보관됩니다. (실행 + 보관) × 프로세스당 보관 출력은 64 MiB를 넘을 수 없으며, 이를 초과하게 되는 변경은 거부됩니다 |

### adb 포트 포워딩으로 사용하기

서버가 `127.0.0.1`에 바인딩된 경우(기본값, 가장 안전):

```bash
# 기기 포트를 호스트로 포워딩
adb forward tcp:8080 tcp:8080

# 호스트에서 연결 테스트
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"ping"}'
```

### 네트워크로 사용하기

서버가 `0.0.0.0`에 바인딩된 경우:

1. 기기의 IP 주소를 확인합니다 (앱의 연결 정보에 표시됨)
2. Bearer 토큰과 함께 `POST http://DEVICE_IP:8080/mcp`로 직접 연결합니다

**경고**: `0.0.0.0`에 바인딩하면 같은 네트워크의 모든 기기에 서버가 노출됩니다. 신뢰할 수 있는 사설 네트워크에서만 사용하세요.

하나의 주소에서만 수신하려면(예: Tailscale 같은 VPN에서의 기기 주소) **특정 IP**를 선택하고 기기의 IP 주소를 입력하세요. 서버는 해당 주소에서만 수신하므로(터널이 사용 설정된 경우 터널을 위해 `127.0.0.1` 추가) 보통 그 주소가 속한 네트워크에서만 접근할 수 있으며, 기기에 해당 주소가 없는 동안에는 서버가 시작되지 않습니다.

### 원격 접근 터널 사용하기

포트 포워딩 없이 로컬 네트워크 외부에서 연결하려면:

1. **무료 E2E 터널** (기본값, 계정 불필요): 종단 간 암호화됩니다. 휴대전화가 하드웨어 키 증명으로 등록해 `d.arc-tunnel.io` 아래의 이름과 자신의 키로 발급된 공개 인증서(Let's Encrypt, Google Trust Services 또는 ZeroSSL)를 받고, TLS를 직접 종료합니다. 터널 서비스는 암호문만 중계합니다(트래픽을 읽는 Cloudflare나 ngrok과 다름). URL 옆의 새로고침 아이콘을 누르면 새 이름으로 다시 등록합니다. 인증서 투명성(CT) 확인(기본적으로 켜짐)은 내 이름의 인증서가 다른 키로 발급되면 경고합니다. 상태 확인(기본적으로 켜짐)은 서버가 실행 중이거나 서버 화면이 열려 있는 동안 최대 30분마다 공개 상태 페이지를 읽고, 서비스에 문제가 보고되면 서버 화면과 알림으로 경고합니다. 자체 호스팅하는 경우 기본 주소를 변경합니다. 자체 호스팅에는 tunneld 0.8.0 이상이 필요합니다.
2. **Cloudflare Quick Tunnels** (계정 불필요): 무작위 `*.trycloudflare.com` HTTPS URL을 가진 임시 터널을 만듭니다.
3. **ngrok** (계정 필요): 선택적으로 커스텀 도메인을 지원합니다. ngrok 인증 토큰이 필요합니다(무료 요금제 제공). arm64-v8a 및 x86_64 기기에서 사용할 수 있습니다.

Cloudflare와 ngrok은 `http://localhost:<터널 로컬 포트>`(설정 → 터널)로 전달하므로 서버가 HTTPS를 제공하는 동안에도 동작합니다. 이때 서버는 이들을 위해 `127.0.0.1`에 별도의 일반 HTTP 리스너를 엽니다.

앱의 "원격 접근" 섹션에서 터널을 사용 설정하세요. 공개 URL은 연결 정보와 서버 로그에 표시됩니다.

### ADB를 통한 헤드리스 설정

UI를 열지 않고 명령줄에서 앱을 완전히 구성하고 제어할 수 있습니다. 자동화된 설정, CI 파이프라인, 헤드리스 기기에 유용합니다.

`<app-id>`를 사용하는 빌드의 애플리케이션 ID로 바꾸세요.
- **Debug (gms 버전)**: `com.danielealbano.androidremotecontrolmcp.gms.debug`
- **Debug (foss 버전)**: `com.danielealbano.androidremotecontrolmcp.foss.debug`
- **Release (두 버전 공통)**: `com.danielealbano.androidremotecontrolmcp`

#### 권한 부여

```bash
# 접근성 서비스 사용 설정 (UI 조회, 동작, 스크린샷에 필수)
adb shell settings put secure enabled_accessibility_services \
  <app-id>/com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService

# 알림 리스너 서비스 사용 설정 (알림 도구에 필수)
adb shell cmd notification allow_listener \
  <app-id>/com.danielealbano.androidremotecontrolmcp.services.notifications.McpNotificationListenerService

# 알림 권한 부여 (Android 13 이상)
adb shell pm grant <app-id> android.permission.POST_NOTIFICATIONS

# 카메라 권한 부여
adb shell pm grant <app-id> android.permission.CAMERA

# 마이크 권한 부여
adb shell pm grant <app-id> android.permission.RECORD_AUDIO

# 위치 권한 부여
adb shell pm grant <app-id> android.permission.ACCESS_FINE_LOCATION
adb shell pm grant <app-id> android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant <app-id> android.permission.ACCESS_BACKGROUND_LOCATION

# 근처 WiFi 기기 권한 부여 (Android 13 이상)
adb shell pm grant <app-id> android.permission.NEARBY_WIFI_DEVICES

# 미디어 읽기 권한 부여 (Android 13 이상)
adb shell pm grant <app-id> android.permission.READ_MEDIA_IMAGES
adb shell pm grant <app-id> android.permission.READ_MEDIA_VIDEO
adb shell pm grant <app-id> android.permission.READ_MEDIA_AUDIO
```

전체 레퍼런스와 모든 권한을 한 번에 부여하는 복사·붙여넣기용 스크립트는 [docs/PERMISSIONS.md](docs/PERMISSIONS.md)를 참고하세요.

#### 앱 구성

모든 extra는 선택 사항이며, 전달한 항목만 업데이트됩니다. 앱이 열려 있을 필요는 **없습니다.**

> **adb가 필요합니다.** 구성 리시버와 서비스 트램펄린은 `android.permission.DUMP` 권한으로
> 보호되는데, 이 권한은 adb shell UID는 가지고 있지만 일반 앱은 얻을 수 없습니다. 아래 명령은
> `adb shell`에서 그대로 동작하며, 기기의 앱은 이를 이용해 서버를 재구성할 수 없습니다.
> [보안](#보안) 섹션을 참고하세요.

```bash
adb shell am broadcast \
  -a com.danielealbano.androidremotecontrolmcp.ADB_CONFIGURE \
  -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbConfigReceiver \
  --es bearer_token "my-secret-token" \
  --es binding_address "0.0.0.0" \
  --ei port 8080 \
  --ez auto_start_on_boot true \
  --ez hide_from_recents false \
  --ez https_enabled false \
  --es certificate_source "AUTO_GENERATED" \
  --es certificate_hostname "mcp.local" \
  --ez tunnel_enabled true \
  --es tunnel_provider "android-remote-control-mcp-tunneld" \
  --es arc_tunnel_base_address "arc-tunnel.io" \
  --ez arc_tunnel_ct_check_enabled true \
  --ez arc_tunnel_status_check_enabled true \
  --es cloudflare_tunnel_mode "TOKEN" \
  --es cloudflare_tunnel_token "your-cloudflare-tunnel-token" \
  --es cloudflare_tunnel_extra_args "--edge region1.v2.argotunnel.com:7844" \
  --es ngrok_authtoken "your-ngrok-token" \
  --es ngrok_domain "your-domain.ngrok-free.app" \
  --ei tunnel_local_port 61200 \
  --ei file_size_limit_mb 50 \
  --ez allow_http_downloads false \
  --ez allow_unverified_https_certs false \
  --ez block_local_network_downloads false \
  --ei download_timeout_seconds 60 \
  --es device_slug "pixel8" \
  --es tool_permissions '{"disabled_tools":["tap"],"disabled_params":{"swipe":["duration_ms"]}}'
```

```bash
# Bearer 인증 사용 중지 (신뢰할 수 있는 네트워크에서만 사용하세요. 서버는 OAuth도
# 사용 중지된 경우에만 개방 상태가 됩니다 — 보안 섹션 참고)
adb shell am broadcast \
  -a com.danielealbano.androidremotecontrolmcp.ADB_CONFIGURE \
  -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbConfigReceiver \
  --ez bearer_token_enabled false
```

Bearer 적용 여부는 값을 지우는 것이 아니라 `--ez bearer_token_enabled <bool>`로 제어됩니다. `bearer_token_enabled=true`인 상태에서 값을 지우면(`--es bearer_token ""`) `/mcp`가 차단 상태(401)로 실패하며, 서버가 개방되지 **않습니다.** 서버는 `bearer_token_enabled`와 `oauth_enabled`가 **둘 다** false인 경우에만 인증되지 않은 요청을 받아들입니다.

| Extra | 타입 | 설명 |
|-------|------|-------------|
| `bearer_token` | string | 정적 Bearer 토큰 값(`bearer_token_enabled=true`인 상태에서 지우면 차단 상태로 실패하며, 인증이 사용 중지되지 않음) |
| `bearer_token_enabled` | boolean | Bearer 토큰 인증 사용 설정/해제(값과 무관하게 적용 여부를 제어) |
| `oauth_enabled` | boolean | 자체 내장 OAuth 2.1 서버 사용 설정/해제(Claude.ai / Claude Desktop 커넥터) |
| `public_url_override` | string | OAuth 메타데이터와 공유 링크에 사용되는 공개 호스트 고정(비어 있음 = 요청에서 자동 감지) |
| `binding_address` | string | `127.0.0.1`(로컬호스트), `0.0.0.0`(네트워크) 또는 기기의 다른 IP 주소 리터럴(특정 IP) |
| `port` | int | HTTP/HTTPS 서버 포트(1-65535) |
| `auto_start_on_boot` | boolean | 기기가 부팅되면 MCP 서버 시작 |
| `hide_from_recents` | boolean | Android 최근 작업 목록에서 앱 제외 |
| `https_enabled` | boolean | TLS를 사용하는 HTTPS 사용 설정 |
| `certificate_source` | string | `AUTO_GENERATED` 또는 `CUSTOM` |
| `certificate_hostname` | string | 자동 생성 인증서의 호스트 이름 |
| `tunnel_enabled` | boolean | 원격 접근 터널 사용 설정 |
| `tunnel_provider` | string | `android-remote-control-mcp-tunneld`(무료 E2E 터널), `CLOUDFLARE` 또는 `NGROK` |
| `arc_tunnel_base_address` | string | 무료 E2E 터널 서비스의 기본 주소(기본값 `arc-tunnel.io`. `enroll.`, `connect.`, `d.` 호스트가 여기서 파생됨) |
| `arc_tunnel_ct_check_enabled` | boolean | 무료 E2E 터널의 인증서 투명성(CT) 확인(기본값 `true`) |
| `arc_tunnel_status_check_enabled` | boolean | 터널이 켜져 있을 때, 서버가 실행 중이거나 서버 화면이 열려 있는 동안 최대 30분마다 수행하는 공개 무료 E2E 터널 서비스 상태 확인(기본값 `true`) |
| `arc_tunnel_enroll` | boolean | `true`이면 앱이 화면에 보이고 서버가 중지된 상태에서 같은 브로드캐스트의 설정으로 무료 E2E 터널을 등록합니다(또는 실패한 인증서 발급을 재개). 헤드리스 환경에서는 아래의 트램펄린 액션 `enroll-tunnel`을 사용하세요. 공개 URL은 서버 탭에 표시됩니다 |
| `cloudflare_tunnel_mode` | string | `FREE`(Quick Tunnel, 무작위 `*.trycloudflare.com` URL) 또는 `TOKEN`(고정 호스트 이름을 가진 명명된 터널) |
| `cloudflare_tunnel_token` | string | Cloudflare 명명된 터널 토큰(`cloudflare_tunnel_mode`가 `TOKEN`일 때 사용) |
| `cloudflare_tunnel_extra_args` | string | cloudflared에 전달할 선택적 추가 인수(예: `--edge region1.v2.argotunnel.com:7844`) |
| `ngrok_authtoken` | string | ngrok 인증 토큰 |
| `ngrok_domain` | string | ngrok 커스텀 도메인(선택 사항) |
| `tunnel_local_port` | int | Cloudflare와 ngrok이 `localhost`에서 전달하는 포트(1-65535) |
| `file_size_limit_mb` | int | 파일 작업의 최대 파일 크기(1-500) |
| `allow_http_downloads` | boolean | HTTPS가 아닌 다운로드 허용 |
| `allow_unverified_https_certs` | boolean | 다운로드 시 검증되지 않은 HTTPS 인증서 허용 |
| `block_local_network_downloads` | boolean | 이 휴대전화, 로컬 네트워크 및 VPN 주소에서의 다운로드 거부 |
| `download_timeout_seconds` | int | 다운로드 제한 시간(10-300) |
| `device_slug` | string | 도구 이름 접두사에 쓰이는 기기 식별자 |
| `tool_permissions` | string (JSON) | 도구별·매개변수별 권한: `{"disabled_tools":["tool_name"],"disabled_params":{"tool_name":["param"]}}` |
| `storage_location_id` | string | 기존 저장 위치의 ID(`android_list_storage_locations` 또는 설정 > 저장소에서 확인). 아래 두 extra에 필요하며, 알 수 없는 ID는 무시됩니다 |
| `storage_allow_write` | boolean | `storage_location_id`로 지정한 위치에 대한 쓰기 작업 허용 |
| `storage_allow_delete` | boolean | `storage_location_id`로 지정한 위치에 대한 삭제 작업 허용 |
| `shell_elevation_enabled` | boolean | 권한 상승 셸 명령 허용 |
| `shell_auto_allow_elevation` | boolean | 모든 권한 상승 셸 명령을 묻지 않고 실행 |
| `shell_blocklist` | string (JSON) | 패턴의 JSON 배열로 표현한 셸 차단 목록, 예: `["*reboot*"]`(`[]`는 목록을 비움. 잘못된 JSON은 무시됨) |
| `shell_allowlist` | string (JSON) | 패턴의 JSON 배열로 표현한 셸 허용 목록(`[]`는 목록을 비움. 잘못된 JSON은 무시됨) |
| `shell_max_running_processes` | int | 최대 실행 셸 프로세스 수(1-64. (실행 + 보관) × 프로세스당 보관 출력은 최대 64 MiB) |
| `shell_max_finished_processes` | int | 보관할 종료 셸 프로세스 수(0-256. (실행 + 보관) × 프로세스당 보관 출력은 최대 64 MiB) |
| `shell_background_timeout_default_minutes` | int | 기본 백그라운드 제한 시간(분, 1부터 최댓값까지) |
| `shell_background_timeout_max_minutes` | int | 최대 백그라운드 제한 시간(분, 1-1440, 기본값보다 작을 수 없음. 둘을 하나의 브로드캐스트로 보낼 수 있음) |
| `shell_approval_timeout_seconds` | int | 승인 제한 시간(초, 10-85) |
| `shell_foreground_wait_seconds` | int | 포그라운드 대기 제한(초, 10-90) |
| `shell_output_per_call_kib` | int | 호출당 반환되는 출력(KiB, 4-256) |
| `shell_output_buffer_kib` | int | 프로세스당 보관 출력(KiB, 64-16384. (실행 + 보관) × 프로세스당 보관 출력은 최대 64 MiB. 하나의 브로드캐스트로 보내는 제한 값들은 순서와 무관) |

#### MCP 서버 시작

서버는 트램펄린 Activity를 통해 시작해야 합니다(Android 12 이상에서 포그라운드 서비스 예외를 얻기 위해 필요). 앱이 강제 종료된 상태에서도 동작합니다.

```bash
adb shell am start \
  -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
  --es action start
```

#### MCP 서버 중지

```bash
adb shell am start \
  -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
  --es action stop
```

#### 무료 E2E 터널 등록

등록(기기의 이름과 인증서를 받는 과정)은 짧은 포그라운드 서비스에서 실행됩니다. 헤드리스 환경에서는 서버 시작과 동일한 트램펄린 Activity를 사용하세요. 실패한 인증서 발급도 재개합니다. 서버가 중지된 상태에서 실행하세요.

```bash
adb shell am start \
  -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
  --es action enroll-tunnel
```

구성 브로드캐스트에 `--ez arc_tunnel_enroll true`를 넣어도 앱이 화면에 보이는 상태라면 같은 동작을 합니다. 백그라운드에서는 Android 12 이상이 포그라운드 서비스 시작을 거부하며 앱은 "background start refused"를 보고합니다.

---

## 권한 참조

앱은 아래 권한을 선언합니다. **일반(Normal)** 권한은 설치 시 자동으로 부여됩니다. **런타임(Runtime)** 권한과 **특수 접근(Special access)** 권한은 사용자의 명시적인 조치가 필요합니다(앱의 설정 > 권한 탭에서, 또는 프로그래밍 방식으로 — [프로그래밍 방식으로 권한 부여](docs/PERMISSIONS.md) 참고). 모든 런타임 권한은 선택 사항입니다. 권한이 없어도 앱은 동작하지만, 해당 권한에 의존하는 기능은 부여하기 전까지 비활성화됩니다. 권한 상승 셸 명령을 위한 선택적 Shizuku 권한은 설정 → 셸(접근 권한 부여) 또는 Shizuku 앱에서 부여합니다.

| 권한 | 유형 | 용도 |
|------------|------|----------|
| `INTERNET` | 일반 | HTTP/HTTPS 서버 및 원격 접근 터널 |
| `ACCESS_NETWORK_STATE` | 일반 | 네트워크 연결 상태 감지 |
| `FOREGROUND_SERVICE` | 일반 | MCP 서버를 포그라운드 서비스로 실행 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 일반 | MCP 서버의 포그라운드 서비스 유형 |
| `FOREGROUND_SERVICE_LOCATION` | 일반 | 이벤트 채널(지오펜스/WiFi)의 포그라운드 서비스 유형 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 일반 | 무료 E2E 터널 등록(이름 + 인증서)의 포그라운드 서비스 유형 |
| `RECEIVE_BOOT_COMPLETED` | 일반 | 기기 부팅 시 서버 자동 시작 |
| `QUERY_ALL_PACKAGES` | 일반 | 앱 관리 도구를 위한 설치된 앱 목록 조회 |
| `KILL_BACKGROUND_PROCESSES` | 일반 | 앱 관리 도구로 백그라운드 앱 중지 |
| `ACCESS_WIFI_STATE` | 일반 | 이벤트 채널을 위한 WiFi 상태 읽기 |
| `CHANGE_WIFI_STATE` | 일반 | 이벤트 채널을 위한 WiFi 관리 |
| `POST_NOTIFICATIONS` | 런타임 | 포그라운드 서비스 알림 표시(Android 13 이상) |
| `CAMERA` | 런타임 | 카메라 사진/동영상 MCP 도구 |
| `RECORD_AUDIO` | 런타임 | 카메라 동영상 도구의 오디오 녹음 |
| `ACCESS_FINE_LOCATION` | 런타임 | 위치 도구 및 지오펜스 이벤트 |
| `ACCESS_COARSE_LOCATION` | 런타임 | 대략적인 위치 |
| `ACCESS_BACKGROUND_LOCATION` | 런타임 | 앱이 백그라운드에 있는 동안의 위치/지오펜스 이벤트 |
| `NEARBY_WIFI_DEVICES` | 런타임 | 이벤트 채널을 위한 WiFi 스캔(Android 13 이상) |
| `READ_MEDIA_IMAGES` | 런타임 | 기본 제공 이미지 저장 위치의 "모든 파일" 모드(Android 13 이상) |
| `READ_MEDIA_VIDEO` | 런타임 | 기본 제공 동영상 저장 위치의 "모든 파일" 모드(Android 13 이상) |
| `READ_MEDIA_AUDIO` | 런타임 | 기본 제공 오디오 저장 위치의 "모든 파일" 모드(Android 13 이상) |
| `moe.shizuku.manager.permission.API_V23` | 런타임 (Shizuku) | Shizuku를 통한 선택적 권한 상승 셸 명령. Shizuku 앱 또는 설정 → 셸 → 접근 권한 부여에서 부여(Shizuku 클라이언트 라이브러리가 선언) |
| 접근성 서비스 | 특수 접근 | UI 조회, 동작 실행, 스크린샷 — 핵심 기능에 **필수** |
| 알림 접근 | 특수 접근 | 알림 도구를 통한 알림 읽기 및 관리 |

---

## 보안

### 루트 불필요

애플리케이션은 전적으로 Android의 표준 권한 모델 안에서 실행됩니다. 루트 접근 권한, 부트로더 잠금 해제, 커스텀 ROM이 필요 없습니다.

### 선택적 권한 상승 셸 명령

셸 도구는 **설정 → 셸 → 권한 상승 명령 허용**(기본값 꺼짐)을 켠 후에만, 앱이 가진 것보다 높은 권한으로 단일 명령을 실행할 수 있습니다. 권한 상승 명령은 [Shizuku](https://shizuku.rikka.app/)를 통해 adb shell 사용자로(Shizuku가 root로 실행 중이면 root로), Shizuku를 사용할 수 없으면 `su`를 통해 root로 실행됩니다. 각 권한 상승 명령에는 클라이언트가 제시한 사유가 포함되며 사용자가 정한 규칙을 거칩니다. 차단 목록은 항상 거부하고, 자동 허용이나 허용 목록은 실행을 허용하며, 그 외에는 휴대전화가 승인을 요청합니다(승인과 거부에는 휴대전화 잠금 해제가 필요). 모든 권한 상승 명령은 서버 로그에 기록됩니다(한 항목은 처음 500바이트까지만 보관하므로 매우 긴 명령의 끝부분은 빠질 수 있음). 앱이 실행 중일 때 이 기능을 켰다면 Shizuku 행의 **앱 다시 시작**을 탭해 Shizuku가 연결될 수 있게 하세요(앱이 스스로 닫혔다가 다시 열리며, 실행 중이던 서버는 다시 시작됩니다). 앱 자체는 루트가 전혀 필요 없으며, Shizuku나 루트는 이 명령들에만 사용됩니다.

일반(권한 상승이 아닌) 셸 명령은 앱 자체의 권한으로 실행됩니다. 설정을 포함한 앱 자신의 파일을 읽고 변경할 수 있으며, 앱에 부여한 `su` 권한이나 Shizuku 접근 권한을 사용할 수 있습니다. 알림, 접근성 또는 인텐트 도구를 사용할 수 있는 MCP 클라이언트는 자신의 권한 상승 요청을 스스로 승인할 수 있으므로, 승인은 사용자를 위한 확인 절차일 뿐 그런 클라이언트를 막는 경계가 아닙니다. 권한 상승 명령이 `su`를 통해 실행되는 동안 앱 프로세스가 죽으면, 일부 루트 관리자(예: Magisk)는 그 명령이 스스로 끝날 때까지 계속 실행되도록 둡니다. 셸 도구는 기본적으로 켜져 있습니다. MCP 클라이언트를 신뢰하지 않는다면 설정 → MCP 도구에서 끄고, 권한 상승은 신뢰하는 클라이언트에만 켜며, 인증은 계속 켜 두세요.

### 신뢰 모델

접근성 서비스가 신뢰 경계입니다. 이 권한을 부여하면 인증된 클라이언트는 사용자와 똑같이 기기를 제어합니다. 화면을 읽고, 앱 자체의 설정과 OAuth 승인 화면을 포함한 모든 컨트롤을 탭할 수 있습니다. 앱 내 토글(위치별 읽기/쓰기/삭제, 도구별 사용 설정/해제, OAuth 승인)은 의도를 표현하고 실수를 방지하는 장치입니다. 이미 접근성 권한을 가진 클라이언트를 가두는 샌드박스가 아니며, 그런 클라이언트는 사용자와 같은 방식으로(UI나 인텐트를 통해) 토글을 바꿀 수 있습니다.

### 인증

인증은 결합형(이중 허용)입니다. `/mcp` 요청은 유효한 정적 Bearer 토큰 **또는** 유효하게 발급된 OAuth 액세스 토큰으로 인가됩니다. 적용 여부는 토큰 값이 아니라 **설정 → 접근**의 독립적인 두 토글 — `bearer_token_enabled`(기본값 켜짐)와 `oauth_enabled`(기본값 켜짐) — 로 제어됩니다. Bearer 토큰은 첫 실행 시 한 번 자동 생성되며(UUID, 앱 업그레이드 후에도 유지) 앱에서 보고, 복사하고, 다시 생성할 수 있습니다. Bearer가 사용 설정된 상태에서 값을 지우면 서버가 차단 상태(401)로 실패합니다. 서버는 두 토글이 **모두** 꺼져 있을 때만 인증되지 않은 요청을 받아들입니다(그렇게 되기 전에 앱이 경고와 확인 창을 표시합니다) — 연동 섹션의 보안 참고 사항을 확인하세요.

### 네트워크 보안

- **기본 바인딩 `127.0.0.1`**: adb 포트 포워딩이나 터널을 통해서만 접근 가능(가장 안전)
- **선택적 바인딩 `0.0.0.0`**: 네트워크를 통해 접근 가능(신뢰할 수 있는 네트워크에서만 사용. 사용 설정 시 보안 경고가 표시됨)
- **선택적 특정 IP**: 해당 주소에서만 수신하므로(사용 설정된 터널을 위한 `127.0.0.1` 추가) 보통 그 주소가 속한 네트워크에서만 접근 가능(동일한 보안 경고)
- **HTTPS**: 선택 사항이며 기본적으로 사용 중지되어 있습니다. 사용 설정하면 자동 생성된 자체 서명 인증서를 사용하거나, CA가 서명한 자체 인증서를 업로드할 수 있습니다. 인증서는 앱 전용 저장소에 보관됩니다.

### 권한

모든 권한은 앱의 설정 > 권한 탭에서 관리합니다. 민감한 권한은 모두 사용자의 명시적인 조치가 필요하며, 어떤 것도 몰래 부여되지 않습니다. 권한 상승 셸 명령을 위한 선택적 Shizuku 접근 권한은 설정 → 셸 또는 Shizuku 앱에서 부여하고, `su` 권한은 루트 관리자가 부여합니다.

저장 위치는 설정 > 저장소에서 구성하며, 자동 위치(예: 다운로드)와 시스템 파일 선택기를 통해 권한을 부여한 사용자 지정 위치가 포함됩니다.

---

## 기여하기

빌드 요구 사항, 테스트, 아키텍처, 개발 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

---

## 라이선스

이 프로젝트는 GNU General Public License 버전 3 또는 (사용자의 선택에 따라) 그 이후 버전에 따라 라이선스가 부여되며,
Google Play 서비스 클라이언트 라이브러리와의 결합을 위해 GPLv3 제7조에 따른 추가 허가가 포함됩니다.
전문은 [LICENSE.md](LICENSE.md)를 참고하세요.

앱에 번들된 서드파티 구성 요소와 그 라이선스는
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)에 나열되어 있습니다.
