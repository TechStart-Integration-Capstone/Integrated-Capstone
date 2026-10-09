'use strict';
// Shared presentation for live personal savings and the offline preview.
window.PayPinkSavingsMilestones = total => {
  total = Math.max(0, Number(total) || 0);
  const million = 1000000;
  const tiers = [10000, 100000, 500000, million, 2 * million];
  if (total >= 2 * million) {
    const current = Math.floor(total / million) * million;
    tiers.push(current, current + million);
  }
  const milestones = [...new Set(tiers)].sort((a, b) => a - b);
  const next = milestones.find(value => value > total);
  const money = value => new Intl.NumberFormat('en-PH', {style:'currency', currency:'PHP'}).format(value);
  const label = value => value >= million ? `₱${value / million}M` : `₱${value / 1000}K`;
  return `<div class="sv-milestones">
    <div class="sv-card-top"><h3>Next milestone: ${label(next)}</h3><span class="sv-plan">${money(next - total)} to go</span></div>
    <div class="sv-progress" role="progressbar" aria-label="Personal savings toward ${label(next)}" aria-valuemin="0" aria-valuemax="${next}" aria-valuenow="${total}" aria-valuetext="${money(total)} of ${money(next)}"><span style="width:${Math.min(100, total / next * 100)}%"></span></div>
    <p class="sv-plan">${money(total)} / ${money(next)}</p>
    <ol class="sv-milestone-list" aria-label="Personal savings milestones">${milestones.map(value => `<li class="${total >= value ? 'sv-milestone-reached' : value === next ? 'sv-milestone-next' : ''}" ${value === next ? 'aria-current="step"' : ''}><strong>${label(value)}</strong><small>${total >= value ? '✓ Reached' : value === next ? 'Up next' : 'Ahead'}</small></li>`).join('')}</ol>
    <p class="sv-plan">Based on your current personal savings. After ₱1M, milestones continue every ₱1M.</p>
  </div>`;
};
