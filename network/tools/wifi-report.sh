#!/bin/bash
# Output dir: $AKDIAG (default ~/akdiag). Created on first run.
mkdir -p "${AKDIAG:-$HOME/akdiag}" 2>/dev/null
# Summarise the newest wifi-monitor run.  ~/akdiag/wifi-report.sh [basename]
B=${1:-$(ls -t ${AKDIAG:-$HOME/akdiag}/wifimon-*.samples 2>/dev/null | head -1)}; B=${B%.samples}
echo "=== $B ==="
echo
echo "--- STATION CHURN (del/new station events per minute) ---"
awk -F'[.:]' '{print $1}' /dev/null 2>/dev/null
grep -E 'new station|del station' "$B.events" 2>/dev/null | awk '{
  ts=strftime("%H:%M",int($1)); if($0~/new station/) n[ts]++; else d[ts]++ }
  END{ for(t in n) printf "  %s  new=%-4d del=%-4d\n", t, n[t], d[t] }' | sort | tail -20
echo "  totals: new=$(grep -c 'new station' "$B.events" 2>/dev/null) del=$(grep -c 'del station' "$B.events" 2>/dev/null)"
echo
echo "--- OTHER nl80211 EVENTS (scan / channel / regulatory) ---"
grep -vE 'new station|del station' "$B.events" 2>/dev/null | sed 's/^[0-9.]*: //' | sort | uniq -c | sort -rn | head -10
echo "  (empty = no scans, no channel changes, no radar events)"
echo
echo "--- POWER RAIL STABILITY ---"
awk -F'\t' 'NR>1 && $8!="" {v=$8+0; a=$9+0; e=$10+0;
  if(vmin==""||v<vmin)vmin=v; if(v>vmax)vmax=v;
  if(amin==""||a<amin)amin=a; if(a>amax)amax=a;
  if(emin==""||e<emin)emin=e; if(e>emax)emax=e; n++}
  END{ printf "  3V7_WL  %.3f - %.3f V   (%.0f mV spread)\n", vmin,vmax,(vmax-vmin)*1000
       printf "  3V7_WL  %.4f - %.4f A\n", amin,amax
       printf "  EXT5V   %.3f - %.3f V   (%.0f mV spread)\n", emin,emax,(emax-emin)*1000
       printf "  samples %d\n", n }' "$B.samples" 2>/dev/null
echo "  throttled values seen: $(awk -F'\t' 'NR>1{print $12}' "$B.samples" 2>/dev/null | sort -u | tr '\n' ' ')"
echo "  temp range: $(awk -F'\t' 'NR>1 && $11!=""{if(m==""||$11<m)m=$11; if($11>M)M=$11}END{print m" - "M" C"}' "$B.samples" 2>/dev/null)"
echo
echo "--- RADIO CONFIG DRIFT ---"
echo "  channels seen: $(awk -F'\t' 'NR>1{print $13}' "$B.samples" 2>/dev/null | sort -u | tr '\n' ' ')"
echo "  txpower seen:  $(awk -F'\t' 'NR>1{print $14}' "$B.samples" 2>/dev/null | sort -u | tr '\n' ' ')"
echo
echo "--- SDIO BUS ERRORS (should all stay 0) ---"
awk -F'\t' 'NR>1{a=$15;b=$16;c=$17;d=$18} END{printf "  tx_sderrs=%s rxc_errors=%s rx_badseq=%s regfails=%s\n",a,b,c,d}' "$B.samples" 2>/dev/null
echo
echo "--- NETDEV ERRORS ---"
awk -F'\t' 'NR>1{e=$5;t=$6;d=$7} END{printf "  rx_errors=%s tx_errors=%s rx_dropped=%s\n",e,t,d}' "$B.samples" 2>/dev/null
echo
echo "--- TRAFFIC: did netdev rx ever stall while stations were associated? ---"
awk -F'\t' 'NR>1 && $2>0 { if(prev!=""&&$3==prev){stall++} else if(prev!=""){if(stall>0){printf "  stalled %d samples (%ds) ending %s\n",stall,stall*2,$1; stall=0}} prev=$3 }
  END{ if(stall>0) printf "  STALLED %d samples (%ds) at end of run\n",stall,stall*2 }' "$B.samples" 2>/dev/null | tail -12
echo
echo "--- FIRMWARE CONSOLE ---"
grep -v '^===' "$B.fwconsole" 2>/dev/null | sed 's/^[0-9]*\.[0-9]*[[:space:]]*//' | grep -v '^$' | sort | uniq -c | sort -rn | head -8
echo
echo "--- KERNEL ---"
sed 's/.*kernel: //' "$B.kernel" 2>/dev/null | sort | uniq -c | sort -rn | head -8
echo "  (empty = the driver logged nothing at all)"
