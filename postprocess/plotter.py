"""
Plotter para los resultados del TP3 - Sistema 1 (Scanning rate en recinto circular).
"""

from __future__ import annotations

import argparse
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


@dataclass
class TimeStep:
    t: float
    x: np.ndarray
    y: np.ndarray
    vx: np.ndarray
    vy: np.ndarray
    state: np.ndarray


@dataclass
class Trajectory:
    N: int
    steps: List[TimeStep] = field(default_factory=list)

    @property
    def times(self) -> np.ndarray:
        return np.array([s.t for s in self.steps])


class Plotter:
    R_OBS = 1.0
    R_PARTICLE = 1.0
    R_RECINTO = 40.0
    SIGMA_OBS = R_OBS + R_PARTICLE
    SIGMA_WALL = R_RECINTO - R_PARTICLE

    _COLOR_FRESH = "#009E73"   # verde
    _COLOR_USED  = "#CC79A7"   # rosa/magenta
    _COLOR_J     = "#D55E00"   # rojo-naranja
    _COLOR_RHO   = "#0072B2"   # azul
    _COLOR_V     = "#E69F00"   # amarillo-naranja

    # Paleta de alto contraste (Okabe-Ito, accesible para daltónicos)
    # Usa colores con fuerte contraste en tono + luminosidad para que se
    # distingan bien incluso en gráficos muy densos.
    _PALETTE = ["#0072B2",  # azul
                "#D55E00",  # rojo-naranja
                "#009E73",  # verde oscuro
                "#CC79A7",  # magenta
                "#F0E442",  # amarillo
                "#56B4E9",  # celeste
                "#E69F00",  # naranja
                "#000000"]  # negro

    _SIM_RE    = re.compile(r"sim_N_(\d+)_run_(\d+)(_light)?\.txt$")
    _CFC_RE    = re.compile(r"cfc_sim_N_(\d+)_run_(\d+)\.txt$")
    _CFC_LIGHT_RE = re.compile(r"cfc_sim_N_(\d+)_run_(\d+)_light\.txt$")
    _FU_RE     = re.compile(r"fu_sim_N_(\d+)_run_(\d+)\.txt$")
    _FU_LIGHT_RE  = re.compile(r"fu_sim_N_(\d+)_run_(\d+)_light\.txt$")
    _RADIAL_RE = re.compile(r"radial_sim_N_(\d+)_run_(\d+)\.txt$")
    _RADIAL_LIGHT_RE = re.compile(r"radial_sim_N_(\d+)_run_(\d+)_light\.txt$")
    _EVENTS_RE = re.compile(r"events_N_(\d+)_run_(\d+)\.txt$")

    def __init__(self, sim_dir="simulations", results_dir="results",
                 out_dir="figures", dS=0.2):
        self.sim_dir = Path(sim_dir)
        self.results_dir = Path(results_dir)
        self.out_dir = Path(out_dir)
        self.dS = dS
        self.out_dir.mkdir(parents=True, exist_ok=True)

    def plot_all(self, regression_window=(0.0, None), fu_ymax=0.4):
        self.plot_execution_time()
        self.plot_cfc_and_J(regression_window=regression_window)
        self.plot_fu(y_max=fu_ymax)
        self.plot_radial_profiles()

    def plot_execution_time(self, timing_csv=None):
        csv_path = Path(timing_csv) if timing_csv else (self.results_dir / "timing.csv")
        fig, ax = plt.subplots(figsize=(7, 5))

        if csv_path.exists():
            Ns, mean_t, std_t = self._read_timing_csv(csv_path)
            ax.errorbar(Ns, mean_t, yerr=std_t, fmt="o-", capsize=4,
                        color=self._COLOR_J, label="Tiempo de ejecución")
            ax.set_ylabel(r"Tiempo de ejecución $\langle T \rangle$ [s]")
            source = f"CSV ({csv_path.name})"
        else:
            per_N = {}
            for path, N, _run in self._iter_sim_files():
                t0 = time.perf_counter()
                self._scan_trajectory_file(path)
                per_N.setdefault(N, []).append(time.perf_counter() - t0)

            if not per_N:
                raise FileNotFoundError(
                    f"No encontré {csv_path} ni archivos de simulación en {self.sim_dir}"
                )
            Ns = np.array(sorted(per_N.keys()))
            mean_t = np.array([np.mean(per_N[N]) for N in Ns])
            std_t = np.array([np.std(per_N[N], ddof=1) if len(per_N[N]) > 1 else 0.0
                              for N in Ns])
            ax.errorbar(Ns, mean_t, yerr=std_t, fmt="o-", capsize=4,
                        color=self._COLOR_J,
                        label="Tiempo de lectura/parseo (proxy)")
            ax.set_ylabel("Tiempo [s]")
            source = "lectura de trayectorias"

        ax.set_xlabel("N (número de partículas)")
        ax.set_title(f"1.1 — Tiempo de ejecución en función de N  [{source}]")
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()
        out = self.out_dir / "1_1_tiempo_vs_N.png"
        fig.savefig(out, dpi=150)
        plt.close(fig)
        print(f"[1.1] → {out}")

        # Gráfico adicional: log(t) vs N
        fig2, ax2 = plt.subplots(figsize=(7, 5))
        pos_mask = mean_t > 0
        ax2.errorbar(
            Ns[pos_mask],
            np.log(mean_t[pos_mask]),
            yerr=std_t[pos_mask] / mean_t[pos_mask],  # propagación: σ_log = σ/μ
            fmt="o-", capsize=4, color=self._COLOR_J,
            label=r"$\log(\langle T \rangle)$",
        )
        ax2.set_xlabel("N (número de partículas)")
        ax2.set_ylabel(r"$\log(\langle T \rangle)$  [log(s)]")
        ax2.set_title("1.1 — log(Tiempo de ejecución) en función de N")
        ax2.grid(True, alpha=0.3)
        ax2.legend()
        fig2.tight_layout()
        out_log = self.out_dir / "1_1_log_tiempo_vs_N.png"
        fig2.savefig(out_log, dpi=150)
        plt.close(fig2)
        print(f"[1.1] → {out_log}")

        # Gráfico adicional: log(t) vs log(N)
        fig3, ax3 = plt.subplots(figsize=(7, 5))
        pos_mask2 = (mean_t > 0) & (Ns > 0)
        log_N = np.log(Ns[pos_mask2])
        log_t = np.log(mean_t[pos_mask2])
        sigma_log_t = std_t[pos_mask2] / mean_t[pos_mask2]  # propagación: σ_log = σ/μ
        ax3.errorbar(
            log_N, log_t, yerr=sigma_log_t,
            fmt="o-", capsize=4, color=self._COLOR_J,
            label=r"$\log(\langle T \rangle)$",
        )
        # Ajuste lineal para estimar el exponente de la ley de potencia T ~ N^alpha
        if len(log_N) >= 2:
            alpha, intercept = np.polyfit(log_N, log_t, 1)
            fit_line = alpha * log_N + intercept
            ax3.plot(log_N, fit_line, "--", color="gray",
                     label=fr"Ajuste: $\alpha = {alpha:.2f}$")
        ax3.set_xlabel(r"$\log(N)$")
        ax3.set_ylabel(r"$\log(\langle T \rangle)$  [log(s)]")
        ax3.set_title("1.1 — log(Tiempo de ejecución) vs log(N)")
        ax3.grid(True, alpha=0.3)
        ax3.legend()
        fig3.tight_layout()
        out_loglog = self.out_dir / "1_1_loglog_tiempo_vs_N.png"
        fig3.savefig(out_loglog, dpi=150)
        plt.close(fig3)
        print(f"[1.1] → {out_loglog}")

        return out

    def plot_cfc_and_J(self, regression_window=(0.0, None)):
        cfc_by_N = self._load_cfc_per_N()
        if not cfc_by_N:
            raise FileNotFoundError(
                f"No encontré archivos cfc_sim_N_*_run_*.txt en {self.results_dir}")

        Ns = sorted(cfc_by_N.keys())
        t_max_global = max(r[0][-1] for runs in cfc_by_N.values() for r in runs)

        fig, ax = plt.subplots(figsize=(8, 5))
        cmap = self._PALETTE
        J_mean, J_std = [], []

        for i, N in enumerate(Ns):
            color = cmap[i % len(cmap)]
            slopes = []
            for j, (t, cfc) in enumerate(cfc_by_N[N]):
                label = f"N={N}" if j == 0 else None
                ax.plot(t, cfc, color=color, alpha=0.55, linewidth=1.0, label=label)
                slopes.append(self._linear_slope(t, cfc, regression_window))
            J_mean.append(np.mean(slopes))
            J_std.append(np.std(slopes, ddof=1) if len(slopes) > 1 else 0.0)

        ax.set_xlabel("t [s]")
        ax.set_ylabel(r"$C_{fc}(t)$  [colisiones acumuladas]")
        ax.set_title("1.2 — Conteo acumulado de partículas frescas → usadas")
        ax.set_xlim(0, t_max_global)
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()
        cfc_path = self.out_dir / "1_2_Cfc_vs_t.png"
        fig.savefig(cfc_path, dpi=150)
        plt.close(fig)
        print(f"[1.2] → {cfc_path}")

        fig, ax = plt.subplots(figsize=(7, 5))
        ax.errorbar(Ns, J_mean, yerr=J_std, fmt="o-", capsize=4,
                    color=self._COLOR_J, markersize=7)
        ax.set_xlabel("N")
        ax.set_ylabel(r"$\langle J \rangle$  [colisiones/s]")
        ax.set_title(r"1.2 — Scanning rate $\langle J \rangle$ en función de N")
        ax.grid(True, alpha=0.3)
        fig.tight_layout()
        j_path = self.out_dir / "1_2_J_vs_N.png"
        fig.savefig(j_path, dpi=150)
        plt.close(fig)
        print(f"[1.2] → {j_path}")

        return cfc_path, j_path

    def plot_fu(self, tail_fraction=0.2, threshold=0.9, y_max=0.4):
        # ── TIEMPOS AL ESTACIONARIO MANUALES ─────────────────────────────────
        # Completar con {N: t_onset} para cada N que se quiera fijar manualmente.
        # Para los N ausentes se usa el modo automático (último tail_fraction).
        # Ejemplo: T_ONSET_MANUAL = {50: 200.0, 100: 350.0, 200: 500.0}
        T_ONSET_MANUAL = {}   # <-- EDITAR AQUI
        # ─────────────────────────────────────────────────────────────────────

        fu_by_N = self._load_fu_per_N()
        if not fu_by_N:
            raise FileNotFoundError(
                f"No encontré archivos fu_sim_N_*_run_*.txt en {self.results_dir}")

        Ns = sorted(fu_by_N.keys())
        t_max_global = max(r[0][-1] for runs in fu_by_N.values() for r in runs)

        fig, ax = plt.subplots(figsize=(8, 5))
        cmap = self._PALETTE

        F_mean, F_std = [], []
        tstat_mean, tstat_std = [], []
        fu_avg_per_N = {}

        for i, N in enumerate(Ns):
            color = cmap[i % len(cmap)]
            Fs, ts = [], []
            t_onset = T_ONSET_MANUAL.get(N, None)
            for j, (t, fu) in enumerate(fu_by_N[N]):
                label = f"N={N}" if j == 0 else None
                ax.plot(t, fu, color=color, alpha=0.75, linewidth=1.0, label=label)
                if t_onset is not None:
                    mask = t >= t_onset
                    Fest = float(np.mean(fu[mask])) if mask.sum() > 0 else float(fu[-1])
                    tstat = t_onset
                else:
                    Fest = self._stationary_value(fu, tail_fraction)
                    tstat = self._time_to_fraction(t, fu, threshold * Fest)
                Fs.append(Fest)
                ts.append(tstat)

            F_mean.append(np.mean(Fs))
            F_std.append(np.std(Fs, ddof=1) if len(Fs) > 1 else 0.0)
            tstat_mean.append(np.mean(ts))
            tstat_std.append(np.std(ts, ddof=1) if len(ts) > 1 else 0.0)

            runs = fu_by_N[N]
            t_common = np.linspace(
                max(r[0][0] for r in runs),
                min(r[0][-1] for r in runs),
                500,
            )
            interp_matrix = np.array([np.interp(t_common, r[0], r[1]) for r in runs])
            fu_avg_per_N[N] = (
                t_common,
                interp_matrix.mean(axis=0),
                interp_matrix.std(axis=0, ddof=1) if len(runs) > 1
                else np.zeros(len(t_common)),
            )

        ax.set_xlabel("t [s]")
        ax.set_ylabel(r"$F_u(t) = N_u(t)/N$")
        ax.set_title("1.3 — Fracción de partículas usadas en el tiempo")
        ax.set_xlim(0, t_max_global)
        ax.set_ylim(0.0, 0.35)
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()
        fu_path = self.out_dir / "1_3_Fu_vs_t.png"
        fig.savefig(fu_path, dpi=150)
        plt.close(fig)
        print(f"[1.3] → {fu_path}")

        fig, ax = plt.subplots(figsize=(7, 5))
        ax.errorbar(Ns, F_mean, yerr=F_std, fmt="o-", capsize=4,
                    color=self._COLOR_USED, markersize=7)
        ax.set_xlabel("N")
        ax.set_ylabel(r"$F_{est}$")
        ax.set_title("1.3 — Valor estacionario de $F_u$ en función de N")
        ax.grid(True, alpha=0.3)
        fig.tight_layout()
        fest_path = self.out_dir / "1_3_Fest_vs_N.png"
        fig.savefig(fest_path, dpi=150)
        plt.close(fig)
        print(f"[1.3] → {fest_path}")

        fig, ax = plt.subplots(figsize=(7, 5))
        ax.errorbar(Ns, tstat_mean, yerr=tstat_std, fmt="s-", capsize=4,
                    color=self._COLOR_FRESH, markersize=7)
        ax.set_xlabel("N")
        ax.set_ylabel(r"$t_{estacionario}$ [s]")
        ax.set_title(f"1.3 — Tiempo al estacionario "
                     f"(cuando $F_u \\geq {threshold}\\,F_{{est}}$)")
        ax.grid(True, alpha=0.3)
        fig.tight_layout()
        tstat_path = self.out_dir / "1_3_tEstacionario_vs_N.png"
        fig.savefig(tstat_path, dpi=150)
        plt.close(fig)
        print(f"[1.3] -> {tstat_path}")

        fig, ax = plt.subplots(figsize=(8, 5))
        for i, N in enumerate(Ns):
            color = cmap[i % len(cmap)]
            t_c, fu_avg, fu_std = fu_avg_per_N[N]
            ax.plot(t_c, fu_avg, color=color, linewidth=2.0, label=f"N={N}")
            ax.fill_between(t_c, fu_avg - fu_std, fu_avg + fu_std,
                            color=color, alpha=0.2)
        ax.set_xlabel("t [s]")
        ax.set_ylabel(r"$\langle F_u \rangle (t)$")
        ax.set_title(r"1.3 --- $\langle F_u \rangle$ promediado entre realizaciones")
        ax.set_xlim(0, t_max_global)
        ax.set_ylim(0.0, 0.3)
        ax.grid(True, alpha=0.3)
        ax.legend()
        fig.tight_layout()
        fu_avg_path = self.out_dir / "1_3_Fu_promedio_vs_t.png"
        fig.savefig(fu_avg_path, dpi=150)
        plt.close(fig)
        print(f"[1.3] -> {fu_avg_path}")

        return fu_path, fest_path, tstat_path, fu_avg_path

    def plot_radial_profiles(self, S_target=2.0):
        radial_by_N = self._load_radial_per_N()
        if not radial_by_N:
            raise FileNotFoundError(
                f"No encontré archivos radial_sim_N_*_run_*.txt en {self.results_dir}")

        Ns = sorted(radial_by_N.keys())

        nLayers = int(np.ceil((self.R_RECINTO - self.SIGMA_OBS) / self.dS))
        S_centers = self.SIGMA_OBS + (np.arange(nLayers) + 0.5) * self.dS
        S_inner = self.SIGMA_OBS + np.arange(nLayers) * self.dS
        S_outer = S_inner + self.dS
        shell_area = np.pi * (S_outer**2 - S_inner**2)

        profiles = {}
        rho_at_target_mean, rho_at_target_std = [], []
        v_at_target_mean, v_at_target_std = [], []
        J_at_target_mean, J_at_target_std = [], []

        fig_profiles, axes = plt.subplots(1, 3, figsize=(15, 4.5))
        cmap = self._PALETTE

        for i, N in enumerate(Ns):
            realizations = radial_by_N[N]
            if not realizations:
                continue

            rho_runs = np.zeros((len(realizations), nLayers))
            v_runs = np.zeros((len(realizations), nLayers))
            J_runs = np.zeros((len(realizations), nLayers))

            for r, snapshots in enumerate(realizations):
                nSnap = len(snapshots)
                count_sum = np.zeros(nLayers)
                v_sum = np.zeros(nLayers)
                v_cnt = np.zeros(nLayers)

                for snap in snapshots:
                    for idx, (c, sv) in snap.items():
                        if 0 <= idx < nLayers:
                            count_sum[idx] += c
                            v_sum[idx] += sv
                            v_cnt[idx] += c

                denom_rho = nSnap * shell_area
                rho_r = np.divide(count_sum, denom_rho,
                                  out=np.zeros_like(count_sum), where=denom_rho > 0)
                v_r = np.divide(np.abs(v_sum), v_cnt,
                                out=np.zeros_like(v_sum), where=v_cnt > 0)
                rho_runs[r] = rho_r
                v_runs[r] = v_r
                J_runs[r] = rho_r * v_r

            rho_mean = rho_runs.mean(axis=0)
            rho_std = rho_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)
            v_mean = v_runs.mean(axis=0)
            v_std = v_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)
            J_mean = J_runs.mean(axis=0)
            J_std = J_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)

            profiles[N] = {"rho": rho_mean, "v": v_mean, "J": J_mean,
                           "rho_std": rho_std, "v_std": v_std, "J_std": J_std}

            color = cmap[i % len(cmap)]
            axes[0].fill_between(S_centers, rho_mean - rho_std, rho_mean + rho_std,
                                 color=color, alpha=0.10, linewidth=0)
            axes[0].plot(S_centers, rho_mean, color=color, linewidth=2.0, label=f"N={N}")
            axes[1].fill_between(S_centers, np.clip(v_mean - v_std, 0, None), v_mean + v_std,
                                 color=color, alpha=0.10, linewidth=0)
            axes[1].plot(S_centers, v_mean, color=color, linewidth=2.0, label=f"N={N}")
            axes[2].fill_between(S_centers, np.clip(J_mean - J_std, 0, None), J_mean + J_std,
                                 color=color, alpha=0.10, linewidth=0)
            axes[2].plot(S_centers, J_mean, color=color, linewidth=2.0, label=f"N={N}")

            idx_target = int(np.argmin(np.abs(S_centers - S_target)))
            rho_at_target_mean.append(rho_mean[idx_target])
            rho_at_target_std.append(rho_std[idx_target])
            v_at_target_mean.append(v_mean[idx_target])
            v_at_target_std.append(v_std[idx_target])
            J_at_target_mean.append(J_mean[idx_target])
            J_at_target_std.append(J_std[idx_target])

        titles = [
            r"1.4 — $\langle \rho^{in}_f \rangle$(S)",
            r"1.4 — $|\langle v^{in}_f \rangle|$(S)",
            r"1.4 — $J_{in}$(S) = $\rho \cdot |v|$",
        ]
        ylabels = [
            r"$\langle \rho^{in}_f \rangle$  [1/m$^2$]",
            r"$|\langle v^{in}_f \rangle|$  [m/s]",
            r"$J_{in}$  [1/(m$^2$·s)]",
        ]
        for ax, title, ylab in zip(axes, titles, ylabels):
            ax.set_xlabel("S [m]")
            ax.set_ylabel(ylab)
            ax.set_title(title)
            ax.grid(True, alpha=0.3)
            ax.legend(fontsize=8)

        fig_profiles.tight_layout()
        prof_path = self.out_dir / "1_4_perfiles_radiales.png"
        fig_profiles.savefig(prof_path, dpi=150)
        plt.close(fig_profiles)
        print(f"[1.4] → {prof_path}")

        fig, axes = plt.subplots(1, 3, figsize=(15, 4.5))
        axes[0].errorbar(Ns, rho_at_target_mean, yerr=rho_at_target_std,
                         fmt="o-", capsize=4, color=self._COLOR_RHO)
        axes[0].set_title(fr"$\langle \rho^{{in}}_f \rangle$ en S$\approx${S_target} m")
        axes[0].set_ylabel(r"$\rho^{in}_f$ [1/m$^2$]")

        axes[1].errorbar(Ns, v_at_target_mean, yerr=v_at_target_std,
                         fmt="s-", capsize=4, color=self._COLOR_V)
        axes[1].set_title(fr"$|\langle v^{{in}}_f \rangle|$ en S$\approx${S_target} m")
        axes[1].set_ylabel(r"$|v^{in}_f|$ [m/s]")

        axes[2].errorbar(Ns, J_at_target_mean, yerr=J_at_target_std,
                         fmt="^-", capsize=4, color=self._COLOR_J)
        axes[2].set_title(fr"$J_{{in}}$ en S$\approx${S_target} m")
        axes[2].set_ylabel(r"$J_{in}$ [1/(m$^2$·s)]")

        for ax in axes:
            ax.set_xlabel("N")
            ax.grid(True, alpha=0.3)
        fig.tight_layout()
        targ_path = self.out_dir / "1_4_observables_S2_vs_N.png"
        fig.savefig(targ_path, dpi=150)
        plt.close(fig)
        print(f"[1.4] → {targ_path}")

        return prof_path, targ_path

    def _iter_sim_files(self):
        for p in sorted(self.sim_dir.glob("sim_N_*_run_*.txt")):
            m = self._SIM_RE.search(p.name)
            if m:
                yield p, int(m.group(1)), int(m.group(2))

    def _scan_trajectory_file(self, path):
        with open(path, "r") as f:
            while True:
                header = f.readline()
                if not header:
                    break
                header = header.strip()
                if not header:
                    continue
                parts = header.split()
                if len(parts) != 2:
                    continue
                try:
                    N = int(parts[0])
                    float(parts[1])
                except ValueError:
                    continue
                for _ in range(N):
                    line = f.readline()
                    if not line:
                        return

    def _parse_trajectory(self, path):
        steps = []
        N = 0
        with open(path, "r") as f:
            while True:
                header = f.readline()
                if not header:
                    break
                header = header.strip()
                if not header:
                    continue
                parts = header.split()
                if len(parts) != 2:
                    continue
                try:
                    N = int(parts[0])
                    t = float(parts[1])
                except ValueError:
                    continue
                block = []
                for _ in range(N):
                    line = f.readline()
                    if not line:
                        block = []
                        break
                    block.append(line)
                if len(block) != N:
                    break
                try:
                    data = np.loadtxt(block)
                except ValueError:
                    continue
                if data.ndim == 1:
                    data = data.reshape(1, -1)
                steps.append(TimeStep(
                    t, data[:, 1].copy(), data[:, 2].copy(),
                    data[:, 3].copy(), data[:, 4].copy(),
                    data[:, 5].astype(np.int32),
                ))
        return Trajectory(N=N, steps=steps)

    @staticmethod
    def _read_tab_table(path):
        rows = []
        with open(path, "r") as f:
            for line in f:
                s = line.strip()
                if not s or s.startswith("#"):
                    continue
                s = s.replace(",", ".")
                tok = s.split()
                try:
                    rows.append([float(x) for x in tok])
                except ValueError:
                    continue
        if not rows:
            return np.empty((0, 0))
        width = min(len(r) for r in rows)
        return np.array([r[:width] for r in rows])

    def _load_events_file(self, path):
        N = 0
        tf = 0.0
        snap_dt = 0.0
        t_obs, t_wall = [], []
        snaps = []
        current_snap = None
        current_t = 0.0
        remaining_lines = 0

        with open(path, "r") as f:
            for raw in f:
                line = raw.strip()
                if not line:
                    continue
                if line.startswith("#"):
                    tok = line.replace(",", ".").split()
                    if len(tok) >= 3 and tok[1] == "N":
                        try: N = int(tok[2])
                        except ValueError: pass
                    elif len(tok) >= 3 and tok[1] == "tf":
                        try: tf = float(tok[2])
                        except ValueError: pass
                    elif len(tok) >= 3 and tok[1] == "radialSnapDt":
                        try: snap_dt = float(tok[2])
                        except ValueError: pass
                    continue

                line = line.replace(",", ".")
                tok = line.split()

                if remaining_lines > 0 and current_snap is not None:
                    if len(tok) >= 5:
                        x = float(tok[0]); y = float(tok[1])
                        vx = float(tok[2]); vy = float(tok[3])
                        state = int(tok[4])
                        if state == 0:
                            r = (x*x + y*y) ** 0.5
                            if r > 0:
                                rdotv = x*vx + y*vy
                                if rdotv < 0:
                                    v_radial = rdotv / r
                                    idx = int((r - self.SIGMA_OBS) / self.dS)
                                    if idx < 0:
                                        idx = 0
                                    prev = current_snap.get(idx, (0, 0.0))
                                    current_snap[idx] = (prev[0] + 1, prev[1] + v_radial)
                    remaining_lines -= 1
                    if remaining_lines == 0:
                        snaps.append((current_t, current_snap))
                        current_snap = None
                    continue

                if tok[0] == "EVT" and len(tok) >= 4:
                    typ = tok[1]
                    t = float(tok[2])
                    if typ == "OBS":
                        t_obs.append(t)
                    elif typ == "WALL":
                        t_wall.append(t)
                elif tok[0] == "SNAP" and len(tok) >= 3:
                    current_t = float(tok[1])
                    remaining_lines = int(tok[2])
                    current_snap = {}
                    if remaining_lines == 0:
                        snaps.append((current_t, current_snap))
                        current_snap = None

        return {
            "N": N, "tf": tf, "snapDt": snap_dt,
            "t_obs": np.array(t_obs),
            "t_wall": np.array(t_wall),
            "snaps": snaps,
        }

    def _has_events_files(self):
        return any(self.results_dir.glob("events_N_*_run_*.txt"))

    def _load_cfc_per_N(self):
        out = {}
        for p in sorted(self.results_dir.glob("events_N_*_run_*.txt")):
            m = self._EVENTS_RE.search(p.name)
            if not m:
                continue
            N = int(m.group(1))
            ev = self._load_events_file(p)
            t_obs = ev["t_obs"]
            t = np.concatenate([[0.0], t_obs, [ev["tf"]]])
            cfc = np.concatenate([[0], np.arange(1, len(t_obs) + 1), [len(t_obs)]])
            out.setdefault(N, []).append((t, cfc.astype(float)))

        for pattern, regex in [
            ("cfc_sim_N_*_run_*.txt", self._CFC_RE),
            ("cfc_sim_N_*_run_*_light.txt", self._CFC_LIGHT_RE),
        ]:
            for p in sorted(self.results_dir.glob(pattern)):
                m = regex.search(p.name)
                if not m:
                    continue
                N = int(m.group(1))
                data = self._read_tab_table(p)
                if data.size == 0:
                    continue
                out.setdefault(N, []).append((data[:, 0], data[:, 1]))
        return out

    def _load_fu_per_N(self):
        out = {}
        for p in sorted(self.results_dir.glob("events_N_*_run_*.txt")):
            m = self._EVENTS_RE.search(p.name)
            if not m:
                continue
            N = int(m.group(1))
            ev = self._load_events_file(p)
            events = [(t, +1) for t in ev["t_obs"]] + [(t, -1) for t in ev["t_wall"]]
            events.sort(key=lambda x: x[0])
            ts, nu = [0.0], [0]
            cur = 0
            for t, d in events:
                cur += d
                ts.append(t); nu.append(cur)
            ts.append(ev["tf"]); nu.append(cur)
            fu = np.array(nu, dtype=float) / max(1, N)
            out.setdefault(N, []).append((np.array(ts), fu))

        for pattern, regex in [
            ("fu_sim_N_*_run_*.txt", self._FU_RE),
            ("fu_sim_N_*_run_*_light.txt", self._FU_LIGHT_RE),
        ]:
            for p in sorted(self.results_dir.glob(pattern)):
                m = regex.search(p.name)
                if not m:
                    continue
                N = int(m.group(1))
                data = self._read_tab_table(p)
                if data.size == 0:
                    continue
                out.setdefault(N, []).append((data[:, 0], data[:, 2]))
        return out

    def _load_radial_per_N(self):
        out = {}
        for p in sorted(self.results_dir.glob("events_N_*_run_*.txt")):
            m = self._EVENTS_RE.search(p.name)
            if not m:
                continue
            N = int(m.group(1))
            ev = self._load_events_file(p)
            snapshots = [snap_dict for (_t, snap_dict) in ev["snaps"]]
            out.setdefault(N, []).append(snapshots)

        for pattern, regex in [
            ("radial_sim_N_*_run_*.txt", self._RADIAL_RE),
            ("radial_sim_N_*_run_*_light.txt", self._RADIAL_LIGHT_RE),
        ]:
            for p in sorted(self.results_dir.glob(pattern)):
                m = regex.search(p.name)
                if not m:
                    continue
                N = int(m.group(1))
                snapshots = []
                current = {}
                in_block = False

                with open(p, "r") as f:
                    for line in f:
                        line = line.rstrip()
                        if line.startswith("# STEP"):
                            if in_block:
                                snapshots.append(current)
                            current = {}
                            in_block = True
                            continue
                        if not line:
                            if in_block:
                                snapshots.append(current)
                                current = {}
                                in_block = False
                            continue
                        if line.startswith("#"):
                            continue
                        tok = line.replace(",", ".").split()
                        if len(tok) < 5:
                            continue
                        try:
                            S_center = float(tok[0])
                            count = int(tok[1])
                            v_fin = float(tok[3])
                        except ValueError:
                            continue
                        idx = int(round((S_center - self.SIGMA_OBS - self.dS / 2) / self.dS))
                        current[idx] = (count, count * v_fin)
                    if in_block:
                        snapshots.append(current)

                out.setdefault(N, []).append(snapshots)
        return out

    @staticmethod
    def _read_timing_csv(csv_path):
        with open(csv_path, "r") as f:
            header = f.readline().strip().split(",")
            ncols = len(header)
            rows_mean_t, rows_std_t, rows_N = [], [], []
            for line in f:
                line = line.strip()
                if not line:
                    continue
                tok = line.split(",")
                if len(tok) == ncols:
                    rows_N.append(float(tok[0]))
                    rows_mean_t.append(float(tok[1]))
                    rows_std_t.append(float(tok[2]) if ncols >= 3 else 0.0)
                elif ncols == 4 and len(tok) == 7:
                    rows_N.append(float(tok[0]))
                    rows_mean_t.append(float(f"{tok[1]}.{tok[2]}"))
                    rows_std_t.append(float(f"{tok[3]}.{tok[4]}"))
                else:
                    raise ValueError(
                        f"No puedo interpretar la fila del CSV '{csv_path.name}': "
                        f"{len(tok)} tokens, header tiene {ncols}. Fila: {line!r}"
                    )
        order = np.argsort(rows_N)
        return (np.array(rows_N)[order],
                np.array(rows_mean_t)[order],
                np.array(rows_std_t)[order])

    @staticmethod
    def _linear_slope(t, y, window):
        tmin, tmax = window
        mask = t >= tmin
        if tmax is not None:
            mask &= t <= tmax
        if mask.sum() < 2:
            return 0.0
        slope, _ = np.polyfit(t[mask], y[mask], 1)
        return float(slope)

    @staticmethod
    def _stationary_value(y, tail_fraction):
        n = len(y)
        if n == 0:
            return 0.0
        cut = max(1, int(n * (1.0 - tail_fraction)))
        return float(np.mean(y[cut:]))

    @staticmethod
    def _time_to_fraction(t, y, target):
        idx = np.argmax(y >= target)
        if y[idx] < target:
            return float(t[-1])
        return float(t[idx])


def _main():
    ap = argparse.ArgumentParser(description="Grafica los .txt del TP3.")
    ap.add_argument("--sim-dir", default="simulations")
    ap.add_argument("--results-dir", default="results")
    ap.add_argument("--out", default="figures")
    ap.add_argument("--dS", type=float, default=0.2)
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--timing", action="store_true")
    ap.add_argument("--cfc", action="store_true")
    ap.add_argument("--fu", action="store_true")
    ap.add_argument("--radial", action="store_true")
    ap.add_argument("--reg-tmin", type=float, default=0.0)
    ap.add_argument("--reg-tmax", type=float, default=None)
    ap.add_argument("--fu-ymax", type=float, default=0.4)
    args = ap.parse_args()

    p = Plotter(sim_dir=args.sim_dir, results_dir=args.results_dir,
                out_dir=args.out, dS=args.dS)

    any_flag = args.timing or args.cfc or args.fu or args.radial
    if args.all or not any_flag:
        p.plot_all(regression_window=(args.reg_tmin, args.reg_tmax),
                   fu_ymax=args.fu_ymax)
        return

    if args.timing: p.plot_execution_time()
    if args.cfc:    p.plot_cfc_and_J(regression_window=(args.reg_tmin, args.reg_tmax))
    if args.fu:     p.plot_fu(y_max=args.fu_ymax)
    if args.radial: p.plot_radial_profiles()


if __name__ == "__main__":
    _main()