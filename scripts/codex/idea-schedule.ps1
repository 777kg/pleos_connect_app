# 기획 에이전트 아이디어 모드(npm run idea:codex)를 Windows 작업 스케줄러에 한 시간마다 등록/해제합니다.
# 로그인한 동안에만 돌고(Codex 로그인 정보가 사용자 프로필에 있음), 이전 실행이 안 끝났으면 이번 회차는 건너뜁니다.
#
# 등록:  powershell -ExecutionPolicy Bypass -File scripts\codex\idea-schedule.ps1
# 해제:  powershell -ExecutionPolicy Bypass -File scripts\codex\idea-schedule.ps1 -Remove
# 상태:  Get-ScheduledTaskInfo -TaskName PleosConnect-Idea
# 로그:  .codex-out\idea-schedule.log (실행마다 덧붙음) / 마지막 보고서: .codex-out\idea-last.md
param([switch]$Remove)

$TaskName = "PleosConnect-Idea"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

if ($Remove) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    "해제했습니다: $TaskName"
    return
}

$node = (Get-Command node -ErrorAction Stop).Source
New-Item -ItemType Directory -Force (Join-Path $Repo ".codex-out") | Out-Null
$log = Join-Path $Repo ".codex-out\idea-schedule.log"
$cmd = "/c cd /d `"$Repo`" && echo ===== %date% %time% >> `"$log`" && `"$node`" scripts\codex\idea.mjs >> `"$log`" 2>&1"

$action = New-ScheduledTaskAction -Execute "cmd.exe" -Argument $cmd -WorkingDirectory $Repo
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Hours 1)
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 50) `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings `
    -Description "Pleos Connect 기획 에이전트: 한 시간마다 새 아이디어 1건 (docs/plan/ideas/backlog)" -Force | Out-Null
"등록했습니다: $TaskName (첫 실행 $((Get-Date).AddMinutes(2).ToString('HH:mm')), 이후 1시간마다)"
