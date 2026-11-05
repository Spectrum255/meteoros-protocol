<#
  make-test-jar.ps1 — 把 build/libs 的正式 jar 打成可在 NeoForge 21.1.234 测试实例里跑的 test jar。

  做的事：复制正式 jar，把【外层】与【内嵌 localizedrain】两个 neoforge.mods.toml 里的
  版本下限 [21.1.265,) 改成 [21.1.234,)，写进测试实例 mods 目录，旧文件留 .bak-<时间戳> 备份。

  只用于本机测试；正式发布物仍是 build/libs 里的原样 jar（265）。

  用法：
    .\tools\make-test-jar.ps1
    .\tools\make-test-jar.ps1 -Jar build\libs\meteorosprotocol-1.0.0.jar -NoBackup
#>
param(
  [string]$Jar  = 'build\libs\meteorosprotocol-1.0.0.jar',
  [string]$Dest = 'D:\mc\.minecraft\versions\Meteoros Test\mods\meteorosprotocol-1.0.0-test.jar',
  [switch]$NoBackup
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $Jar)) { throw "找不到 jar: $Jar" }
$src = (Resolve-Path $Jar).Path
$tmp = Join-Path $env:TEMP ('mp-test-jar-' + [guid]::NewGuid().ToString('N') + '.jar')

function Get-EntryText($zip, $name) {
  $e = $zip.GetEntry($name)
  if (-not $e) { throw "缺少条目: $name" }
  $r = New-Object System.IO.StreamReader($e.Open())
  $t = $r.ReadToEnd(); $r.Close(); return $t
}

# ---- 1. 内嵌 localizedrain jar：替换自家 neoforge.mods.toml 的版本下限 ----
$zr = [System.IO.Compression.ZipFile]::OpenRead($src)
$innerName = ($zr.Entries | Where-Object { $_.FullName -like 'META-INF/jarjar/*.jar' } | Select-Object -First 1).FullName
if (-not $innerName) { $zr.Dispose(); throw 'jar 里没有 META-INF/jarjar/*.jar' }
$innerEntry = $zr.GetEntry($innerName)
$ms = New-Object System.IO.MemoryStream
$s = $innerEntry.Open(); $s.CopyTo($ms); $s.Close(); $zr.Dispose()

$iz = New-Object System.IO.Compression.ZipArchive($ms, [System.IO.Compression.ZipArchiveMode]::Update, $true)
$te = $iz.GetEntry('META-INF/neoforge.mods.toml')
if (-not $te) { $iz.Dispose(); throw '内嵌 jar 缺少 META-INF/neoforge.mods.toml' }
$rd = New-Object System.IO.StreamReader($te.Open()); $text = $rd.ReadToEnd(); $rd.Close()
$patched = $text.Replace('[21.1.265,)', '[21.1.234,)')
if ($patched -eq $text) { throw '内嵌 toml 里没找到 [21.1.265,)（版本号可能已变，脚本需要更新）' }
$te.Delete()
$ne = $iz.CreateEntry('META-INF/neoforge.mods.toml', [System.IO.Compression.CompressionLevel]::Optimal)
$w = New-Object System.IO.StreamWriter($ne.Open()); $w.Write($patched); $w.Close()
$iz.Dispose()
$innerBytes = $ms.ToArray(); $ms.Dispose()

# ---- 2. 重打外层 jar：跳过两份待改条目，其余原样搬运 ----
$zh = [System.IO.Compression.ZipFile]::Open($src, 'Read')
$zo = [System.IO.Compression.ZipFile]::Open($tmp, 'Create')
$outerName = 'META-INF/neoforge.mods.toml'
foreach ($e in $zh.Entries) {
  if ($e.FullName -eq $outerName -or $e.FullName -eq $innerName) { continue }
  $ne = $zo.CreateEntry($e.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
  $os = $ne.Open(); $isv = $e.Open(); $isv.CopyTo($os); $isv.Close(); $os.Close()
}
$ot = Get-EntryText $zh $outerName
$newOt = $ot.Replace('[21.1.265,)', '[21.1.234,)')
if ($newOt -eq $ot) { $zo.Dispose(); $zh.Dispose(); throw '外层 toml 里没找到 [21.1.265,)' }
$ne = $zo.CreateEntry($outerName, [System.IO.Compression.CompressionLevel]::Optimal)
$w = New-Object System.IO.StreamWriter($ne.Open()); $w.Write($newOt); $w.Close()
$ne = $zo.CreateEntry($innerName, [System.IO.Compression.CompressionLevel]::Optimal)
$os = $ne.Open(); $os.Write($innerBytes, 0, $innerBytes.Length); $os.Close()
$zo.Dispose(); $zh.Dispose()

# ---- 3. 校验 ----
$pv = [System.IO.Compression.ZipFile]::OpenRead($tmp)
$outerOk = (Get-EntryText $pv $outerName) -match 'versionRange="\[21\.1\.234,\)"'
$ib = New-Object System.IO.MemoryStream
$ie = $pv.GetEntry($innerName); $xs = $ie.Open(); $xs.CopyTo($ib); $xs.Close(); $ib.Position = 0
$pz = New-Object System.IO.Compression.ZipArchive($ib, [System.IO.Compression.ZipArchiveMode]::Read)
$innerOk = (Get-EntryText $pz 'META-INF/neoforge.mods.toml') -match 'versionRange="\[21\.1\.234,\)"'
$pz.Dispose(); $ib.Dispose(); $pv.Dispose()
if (-not ($outerOk -and $innerOk)) { throw "补丁校验失败 outer=$outerOk inner=$innerOk" }

# ---- 4. 落地（旧文件备份）----
$modsDir = Split-Path $Dest -Parent
if (-not (Test-Path $modsDir)) { New-Item -ItemType Directory -Path $modsDir -Force | Out-Null }
if ((Test-Path $Dest) -and -not $NoBackup) {
  $bak = "$Dest.bak-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
  Copy-Item $Dest $bak -Force
  Write-Host "备份旧 jar -> $bak ($((Get-Item $bak).Length) B)"
}
Copy-Item $tmp $Dest -Force
Remove-Item $tmp -Force
$d = Get-Item $Dest
$zv = [System.IO.Compression.ZipFile]::OpenRead($Dest)
$entryCount = $zv.Entries.Count
$zv.Dispose()
Write-Host "已生成测试 jar: $($d.FullName)"
Write-Host "  大小 $($d.Length) B    时间 $($d.LastWriteTime)"
Write-Host "  outer=[21.1.234,) inner=[21.1.234,)  条目数 $entryCount"
