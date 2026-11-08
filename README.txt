드라이브 뮤직 (개인용 안드로이드 앱)

[처음 실행]
- 설정 → 음악 폴더 공유 링크 + API 키 입력 → 저장하고 폴더 불러오기
  (폴더는 '링크가 있는 모든 사용자(뷰어)'로 공유, API 키는 Google Drive API 사용 설정 후 발급)

[GitHub로 APK 빌드]
1) 이 폴더 안의 모든 파일을 새 저장소에 올립니다. (.github 폴더는 자동으로 안 올라갈 수 있어요)
2) 저장소에서 Add file → Create new file → 이름에 .github/workflows/build.yml 입력 → yml 내용을 붙여넣고 Commit
3) Actions 탭 → Build APK → 완료되면 아래 Artifacts의 DriveMusic-apk 다운로드 → APK 설치
- 고정 서명키(app/release.jks)가 들어 있어서, 같은 키로 계속 덮어설치(업데이트)됩니다.
