드라이브 뮤직 (개인용 안드로이드 앱)

[처음 실행]
- 처음 켜면 설정 탭이 열립니다. 음악 폴더 공유 링크 + API 키를 넣고 [저장하고 폴더 불러오기]
  (폴더는 '링크가 있는 모든 사용자(뷰어)'로 공유, API 키는 Google Drive API 사용 설정 후 발급)
- 한 번 열어 본 폴더는 기기에 저장되어 인터넷 없이도 열립니다. 새로 올린 곡은 설정의 [새로 불러오기]를 누르세요.

[API 키 제한]
- 이 앱은 요청마다 패키지 이름(com.driveplayer.music)과 서명 지문(SHA-1)을 같이 보냅니다.
- 앱이 정상 동작하는 걸 확인한 뒤, 콘솔에서 '애플리케이션 제한사항 → Android 앱'에
  패키지 이름과 SHA-1 38:D0:90:21:96:98:66:9B:51:F0:F9:C6:1F:82:26:A2:E3:FC:BE:20 을 등록하세요.

[GitHub로 APK 빌드]
1) 이 폴더 안의 모든 파일을 새 저장소(비공개 권장)에 올립니다. (.github 폴더는 자동으로 안 올라갈 수 있어요)
2) 저장소에서 Add file → Create new file → 이름에 .github/workflows/build.yml 입력 → yml 내용을 붙여넣고 Commit
3) Actions 탭 → Build APK → 완료되면 아래 Artifacts의 DriveMusic-apk 다운로드 → APK 설치
- 고정 서명키(app/release.jks)가 들어 있어서, 같은 키로 계속 덮어설치(업데이트)됩니다.
