"""
Plotter para los resultados del TP3 - Sistema 1 (Scanning rate en recinto circular).

Lee los archivos .txt generados por el motor de simulación (simulations/sim_N_*_run_*.txt)
y/o los archivos de observables creados por SimulationAnalyzer
(results/cfc_*.txt, results/fu_*.txt, results/radial_*.txt) y produce
las figuras pedidas en los puntos 1.1 a 1.4 del enunciado.

Uso típico:
    from plotter import Plotter

    p = Plotter(sim_dir="simulations", results_dir="results", out_dir="figures")
    p.plot_all()                           # corre todo
    # o, selectivamente:
    p.plot_execution_time()                # 1.1
    p.plot_cfc_and_J(regression_window=(1.0, None))   # 1.2
    p.plot_fu()                            # 1.3
    p.plot_radial_profiles()               # 1.4

Desde línea de comandos:
    python plotter.py --sim-dir simulations --results-dir results --out figures --all

Requiere: numpy, matplotlib.
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
# Backend no interactivo: evita que el script se cuelgue en un event loop
# de GUI (MacOSX / Qt) después de guardar la última figura.
matplotlib.use("Agg")
import matplotlib.pyplot as plt


# ─────────────────────────────────────────────────────────────────────────────
#  Estructuras de datos
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class TimeStep:
    """Un snapshot (t) con posiciones/velocidades/estados de las N partículas."""
    t: float
    x: np.ndarray   # shape (N,)
    y: np.ndarray
    vx: np.ndarray
    vy: np.ndarray
    state: np.ndarray  # 0 = fresca, 1 = usada


@dataclass
class Trajectory:
    """Trayectoria completa parseada de un sim_N_*_run_*.txt."""
    N: int
    steps: List[TimeStep] = field(default_factory=list)

    @property
    def times(self) -> np.ndarray:
        return np.array([s.t for s in self.steps])


# ─────────────────────────────────────────────────────────────────────────────
#  Clase principal
# ─────────────────────────────────────────────────────────────────────────────

class Plotter:
    """
    Clase para graficar los .txt generados por el motor de simulación y el
    analizador. Produce las figuras requeridas por los puntos 1.1, 1.2, 1.3 y 1.4
    del enunciado.
    """

    # Parámetros físicos (deben coincidir con los del simulador)
    R_OBS = 1.0
    R_PARTICLE = 1.0
    R_RECINTO = 40.0
    SIGMA_OBS = R_OBS + R_PARTICLE          # 2.0
    SIGMA_WALL = R_RECINTO - R_PARTICLE     # 39.0

    # Estilo
    _COLOR_FRESH = "#2ecc71"
    _COLOR_USED = "#9b59b6"
    _COLOR_J = "#e74c3c"
    _COLOR_RHO = "#3498db"
    _COLOR_V = "#f39c12"

    # Regex para los nombres de archivo (versión normal y versión light)
    _SIM_RE    = re.compile(r"sim_N_(\d+)_run_(\d+)(_light)?\.txt$")
    _CFC_RE    = re.compile(r"cfc_sim_N_(\d+)_run_(\d+)\.txt$")
    _CFC_LIGHT_RE = re.compile(r"cfc_sim_N_(\d+)_run_(\d+)_light\.txt$")
    _FU_RE     = re.compile(r"fu_sim_N_(\d+)_run_(\d+)\.txt$")
    _FU_LIGHT_RE  = re.compile(r"fu_sim_N_(\d+)_run_(\d+)_light\.txt$")
    _RADIAL_RE = re.compile(r"radial_sim_N_(\d+)_run_(\d+)\.txt$")
    _RADIAL_LIGHT_RE = re.compile(r"radial_sim_N_(\d+)_run_(\d+)_light\.txt$")
    # Archivo ligero producido por Simulator.runStream  (events_N_<N>_run_<r>.txt)
    _EVENTS_RE = re.compile(r"events_N_(\d+)_run_(\d+)\.txt$")

    def __init__(
        self,
        sim_dir: str = "simulations",
        results_dir: str = "results",
        out_dir: str = "figures",
        dS: float = 0.2,
    ):
        self.sim_dir = Path(sim_dir)
        self.results_dir = Path(results_dir)
        self.out_dir = Path(out_dir)
        self.dS = dS
        self.out_dir.mkdir(parents=True, exist_ok=True)

    # ── API pública ────────────────────────────────────────────────────────

    def plot_all(
        self,
        regression_window: Tuple[float, Optional[float]] = (0.0, None),
        fu_ymax: float = 0.4,
    ) -> None:
        """Genera todas las figuras (1.1 a 1.4)."""
        self.plot_execution_time()
        self.plot_cfc_and_J(regression_window=regression_window)
        self.plot_fu(y_max=fu_ymax)
        self.plot_radial_profiles()

    # ── 1.1 ── Tiempo de ejecución vs N ──────────────────────────────────

    def plot_execution_time(self, timing_csv: Optional[str] = None) -> Path:
        """
        Punto 1.1: grafica tiempo de ejecución vs N.

        Si existe un CSV con columnas (N, avg_time_s, std_time_s) lo usa
        directamente; si no, mide el tiempo de lectura del archivo de simulación
        como proxy del cómputo realizado.
        """
        csv_path = Path(timing_csv) if timing_csv else (self.results_dir / "timing.csv")
        fig, ax = plt.subplots(figsize=(7, 5))

        if csv_path.exists():
            Ns, mean_t, std_t = self._read_timing_csv(csv_path)
            ax.errorbar(Ns, mean_t, yerr=std_t, fmt="o-", capsize=4,
                        color=self._COLOR_J, label="Tiempo de ejecución")
            ax.set_ylabel(r"Tiempo de ejecución $\langle T \rangle$ [s]")
            source = f"CSV ({csv_path.name})"
        else:
            per_N: Dict[int, List[float]] = {}
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
        return out

    # ── 1.2 ── Cfc(t) y ⟨J⟩(N) ─────────────────────────────────────────────

    def plot_cfc_and_J(
        self,
        regression_window: Tuple[float, Optional[float]] = (0.0, None),
    ) -> Tuple[Path, Path]:
        """
        Punto 1.2: grafica Cfc(t) (varias realizaciones agrupadas por N) y
        ⟨J⟩(N) con barras de error.

        regression_window: (t_min, t_max) para el ajuste lineal de Cfc(t).
                           Usar t_max=None para llegar hasta el final.
        """
        cfc_by_N = self._load_cfc_per_N()
        if not cfc_by_N:
            raise FileNotFoundError(
                f"No encontré archivos cfc_sim_N_*_run_*.txt en {self.results_dir}")

        Ns = sorted(cfc_by_N.keys())

        fig, ax = plt.subplots(figsize=(8, 5))
        cmap = plt.cm.viridis
        J_mean = []
        J_std = []

        for i, N in enumerate(Ns):
            color = cmap(i / max(1, len(Ns) - 1))
            slopes = []
            for j, (t, cfc) in enumerate(cfc_by_N[N]):
                label = f"N={N}" if j == 0 else None
                ax.plot(t, cfc, color=color, alpha=0.55, linewidth=1.0, label=label)

                J = self._linear_slope(t, cfc, regression_window)
                slopes.append(J)

            J_mean.append(np.mean(slopes))
            J_std.append(np.std(slopes, ddof=1) if len(slopes) > 1 else 0.0)

        ax.set_xlabel("t [s]")
        ax.set_ylabel(r"$C_{fc}(t)$  [colisiones acumuladas]")
        ax.set_title("1.2 — Conteo acumulado de partículas frescas → usadas")
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

    # ── 1.3 ── Fu(t), t_estacionario, F_est(N) ────────────────────────────

    def plot_fu(
        self,
        tail_fraction: float = 0.2,
        threshold: float = 0.9,
        y_max: float = 0.4,
    ) -> Tuple[Path, Path, Path]:
        """
        Punto 1.3: evolución de Fu(t), reporta F_est y t_estacionario para cada N.
        Los promedios del estacionario se calculan con la fracción final
        `tail_fraction` de cada serie, y t_estacionario es el primer instante
        en que Fu alcanza `threshold * F_est`.
        """
        fu_by_N = self._load_fu_per_N()
        if not fu_by_N:
            raise FileNotFoundError(
                f"No encontré archivos fu_sim_N_*_run_*.txt en {self.results_dir}")

        Ns = sorted(fu_by_N.keys())

        fig, ax = plt.subplots(figsize=(8, 5))
        cmap = plt.cm.plasma

        F_mean, F_std = [], []
        tstat_mean, tstat_std = [], []

        for i, N in enumerate(Ns):
            color = cmap(i / max(1, len(Ns) - 1))
            Fs, ts = [], []
            for j, (t, fu) in enumerate(fu_by_N[N]):
                label = f"N={N}" if j == 0 else None
                ax.plot(t, fu, color=color, alpha=0.55, linewidth=1.0, label=label)
                Fest = self._stationary_value(fu, tail_fraction)
                tstat = self._time_to_fraction(t, fu, threshold * Fest)
                Fs.append(Fest)
                ts.append(tstat)

            F_mean.append(np.mean(Fs))
            F_std.append(np.std(Fs, ddof=1) if len(Fs) > 1 else 0.0)
            tstat_mean.append(np.mean(ts))
            tstat_std.append(np.std(ts, ddof=1) if len(ts) > 1 else 0.0)

        ax.set_xlabel("t [s]")
        ax.set_ylabel(r"$F_u(t) = N_u(t)/N$")
        ax.set_title("1.3 — Fracción de partículas usadas en el tiempo")
        ax.set_ylim(0.0, y_max)
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
        print(f"[1.3] → {tstat_path}")

        return fu_path, fest_path, tstat_path

    # ── 1.4 ── Perfiles radiales ρ(S), |v(S)|, Jin(S) ─────────────────────

    def plot_radial_profiles(self, S_target: float = 2.0) -> Tuple[Path, Path]:
        """
        Punto 1.4: promedia ρ_fin, v_fin y Jin sobre tiempos y realizaciones
        para cada N, y grafica los tres perfiles. Además grafica los tres
        observables en la capa cercana a S = S_target en función de N.
        """
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

        profiles: Dict[int, Dict[str, np.ndarray]] = {}
        # Para el gráfico vs N usamos también la dispersión entre realizaciones.
        rho_at_target_mean, rho_at_target_std = [], []
        v_at_target_mean, v_at_target_std = [], []
        J_at_target_mean, J_at_target_std = [], []

        fig_profiles, axes = plt.subplots(1, 3, figsize=(15, 4.5))
        cmap = plt.cm.cividis

        for i, N in enumerate(Ns):
            realizations = radial_by_N[N]   # List[List[snapshot_dict]]
            if not realizations:
                continue

            # Calculo ρ_r(S), |v_r(S)|, J_r(S) por realización, promediando
            # todos los snapshots de esa realización.
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

            # Media y σ entre realizaciones
            rho_mean = rho_runs.mean(axis=0)
            rho_std = rho_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)
            v_mean = v_runs.mean(axis=0)
            v_std = v_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)
            J_mean = J_runs.mean(axis=0)
            J_std = J_runs.std(axis=0, ddof=1) if len(realizations) > 1 else np.zeros(nLayers)

            profiles[N] = {"rho": rho_mean, "v": v_mean, "J": J_mean,
                           "rho_std": rho_std, "v_std": v_std, "J_std": J_std}

            color = cmap(i / max(1, len(Ns) - 1))
            # Bandas de error (gris claro, coloreado por N encima para que se distinga)
            axes[0].fill_between(S_centers, rho_mean - rho_std, rho_mean + rho_std,
                                 color=color, alpha=0.18, linewidth=0)
            axes[0].plot(S_centers, rho_mean, color=color, label=f"N={N}")
            axes[1].fill_between(S_centers, np.clip(v_mean - v_std, 0, None), v_mean + v_std,
                                 color=color, alpha=0.18, linewidth=0)
            axes[1].plot(S_centers, v_mean, color=color, label=f"N={N}")
            axes[2].fill_between(S_centers, np.clip(J_mean - J_std, 0, None), J_mean + J_std,
                                 color=color, alpha=0.18, linewidth=0)
            axes[2].plot(S_centers, J_mean, color=color, label=f"N={N}")

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

    # ─────────────────────────────────────────────────────────────────────
    #  Parseo de archivos
    # ─────────────────────────────────────────────────────────────────────

    def _iter_sim_files(self):
        for p in sorted(self.sim_dir.glob("sim_N_*_run_*.txt")):
            m = self._SIM_RE.search(p.name)
            if m:
                yield p, int(m.group(1)), int(m.group(2))

    def _scan_trajectory_file(self, path: Path) -> None:
        """
        Recorre el archivo sin construir la trayectoria completa.
        Sirve para medir costo de lectura/parseo como proxy.
        """
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

    def _parse_trajectory(self, path: Path) -> Trajectory:
        """Parsea sim_N_*_run_*.txt de forma más rápida usando bloques."""
        steps: List[TimeStep] = []
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

                xs = data[:, 1].copy()
                ys = data[:, 2].copy()
                vxs = data[:, 3].copy()
                vys = data[:, 4].copy()
                states = data[:, 5].astype(np.int32)

                steps.append(TimeStep(t, xs, ys, vxs, vys, states))

        return Trajectory(N=N, steps=steps)

    @staticmethod
    def _read_tab_table(path: Path) -> np.ndarray:
        """
        Lee un archivo separado por tabs (o cualquier whitespace), ignorando
        líneas vacías y comentarios (#). Reemplaza la coma decimal por punto
        para tolerar el locale AR/ES del Java. Devuelve una matriz (R×C).
        """
        rows: List[List[float]] = []
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

    def _load_events_file(self, path: Path) -> Dict[str, object]:
        """
        Parsea un archivo events_N_*_run_*.txt (formato streaming del simulador).
        Devuelve dict con:
          N, tf, snapDt,
          t_obs, t_wall       (arrays de tiempos de cambio fresca↔usada)
          snaps               ([(t, shell_idx → (count, sumV))], ordenados por t)
        """
        N = 0
        tf = 0.0
        snap_dt = 0.0
        t_obs: List[float] = []
        t_wall: List[float] = []
        snaps: List[Tuple[float, Dict[int, Tuple[int, float]]]] = []

        current_snap: Optional[Dict[int, Tuple[int, float]]] = None
        current_t: float = 0.0
        remaining_lines = 0

        with open(path, "r") as f:
            for raw in f:
                line = raw.strip()
                if not line:
                    continue
                if line.startswith("#"):
                    # Metadata: "# N 800", "# tf 1000.0", "# radialSnapDt 0.5"
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
                    # Línea dentro de un SNAP: x y vx vy state
                    if len(tok) >= 5:
                        x = float(tok[0]); y = float(tok[1])
                        vx = float(tok[2]); vy = float(tok[3])
                        state = int(tok[4])
                        if state == 0:
                            r = (x*x + y*y) ** 0.5
                            if r > 0:
                                rdotv = x*vx + y*vy
                                if rdotv < 0:
                                    v_radial = rdotv / r    # negativo, hacia el centro
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

    def _has_events_files(self) -> bool:
        return any(self.results_dir.glob("events_N_*_run_*.txt"))

    def _load_cfc_per_N(self) -> Dict[int, List[Tuple[np.ndarray, np.ndarray]]]:
        """
        Lee cfc_sim_N_*_run_*.txt, cfc_sim_N_*_run_*_light.txt y events_*.txt
        → {N: [(t, cfc), ...]}. Las tres fuentes se combinan si coexisten.
        """
        out: Dict[int, List[Tuple[np.ndarray, np.ndarray]]] = {}

        # Fuente 1: archivos events_* (runStream)
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

        # Fuente 2: archivos cfc_* normales y _light
        for pattern, regex in [
            ("cfc_sim_N_*_run_*.txt",       self._CFC_RE),
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
                t = data[:, 0]
                cfc = data[:, 1]
                out.setdefault(N, []).append((t, cfc))

        return out

    def _load_fu_per_N(self) -> Dict[int, List[Tuple[np.ndarray, np.ndarray]]]:
        """
        Lee fu_sim_N_*_run_*.txt, fu_sim_N_*_run_*_light.txt y events_*.txt
        → {N: [(t, fu), ...]}. Las tres fuentes se combinan si coexisten.
        """
        out: Dict[int, List[Tuple[np.ndarray, np.ndarray]]] = {}

        # Fuente 1: archivos events_* (runStream)
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

        # Fuente 2: archivos fu_* normales y _light
        for pattern, regex in [
            ("fu_sim_N_*_run_*.txt",       self._FU_RE),
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
                t = data[:, 0]
                fu = data[:, 2]
                out.setdefault(N, []).append((t, fu))

        return out

    def _load_radial_per_N(self) -> Dict[int, List[List[Dict[int, Tuple[int, float]]]]]:
        """
        Lee radial_sim_N_*_run_*.txt, radial_sim_N_*_run_*_light.txt y events_*.txt
        → {N: [realization_1_snaps, realization_2_snaps, ...]}.
        Las tres fuentes se combinan si coexisten.
        """
        out: Dict[int, List[List[Dict[int, Tuple[int, float]]]]] = {}

        # Fuente 1: archivos events_* (runStream)
        for p in sorted(self.results_dir.glob("events_N_*_run_*.txt")):
            m = self._EVENTS_RE.search(p.name)
            if not m:
                continue
            N = int(m.group(1))
            ev = self._load_events_file(p)
            snapshots = [snap_dict for (_t, snap_dict) in ev["snaps"]]
            out.setdefault(N, []).append(snapshots)

        # Fuente 2: archivos radial_* normales y _light
        for pattern, regex in [
            ("radial_sim_N_*_run_*.txt",       self._RADIAL_RE),
            ("radial_sim_N_*_run_*_light.txt", self._RADIAL_LIGHT_RE),
        ]:
            for p in sorted(self.results_dir.glob(pattern)):
                m = regex.search(p.name)
                if not m:
                    continue
                N = int(m.group(1))
                snapshots: List[Dict[int, Tuple[int, float]]] = []
                current: Dict[int, Tuple[int, float]] = {}
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

    # ─────────────────────────────────────────────────────────────────────
    #  Lectura robusta del timing.csv
    # ─────────────────────────────────────────────────────────────────────

    @staticmethod
    def _read_timing_csv(csv_path: Path) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        Parsea timing.csv tolerante al bug de locale: si Java se ejecutó en una
        JVM con Locale AR/ES, los decimales se escribieron con coma y el archivo
        quedó con 7 columnas por fila en vez de 4.
        """
        with open(csv_path, "r") as f:
            header = f.readline().strip().split(",")
            ncols = len(header)
            rows_mean_t = []
            rows_std_t = []
            rows_N = []
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

    # ─────────────────────────────────────────────────────────────────────
    #  Utilidades estadísticas
    # ─────────────────────────────────────────────────────────────────────

    @staticmethod
    def _linear_slope(
        t: np.ndarray,
        y: np.ndarray,
        window: Tuple[float, Optional[float]],
    ) -> float:
        """Pendiente por mínimos cuadrados de y(t) en la ventana indicada."""
        tmin, tmax = window
        mask = t >= tmin
        if tmax is not None:
            mask &= t <= tmax
        if mask.sum() < 2:
            return 0.0
        slope, _ = np.polyfit(t[mask], y[mask], 1)
        return float(slope)

    @staticmethod
    def _stationary_value(y: np.ndarray, tail_fraction: float) -> float:
        """Promedio de la fracción final `tail_fraction` como estimador del estacionario."""
        n = len(y)
        if n == 0:
            return 0.0
        cut = max(1, int(n * (1.0 - tail_fraction)))
        return float(np.mean(y[cut:]))

    @staticmethod
    def _time_to_fraction(t: np.ndarray, y: np.ndarray, target: float) -> float:
        """Primer instante en el que y(t) ≥ target (si no llega, devuelve t[-1])."""
        idx = np.argmax(y >= target)
        if y[idx] < target:
            return float(t[-1])
        return float(t[idx])


# ─────────────────────────────────────────────────────────────────────────────
#  CLI
# ─────────────────────────────────────────────────────────────────────────────

def _main():
    ap = argparse.ArgumentParser(description="Grafica los .txt del TP3.")
    ap.add_argument("--sim-dir", default="simulations",
                    help="Directorio con sim_N_*_run_*.txt")
    ap.add_argument("--results-dir", default="results",
                    help="Directorio con cfc_*/fu_*/radial_* generados por SimulationAnalyzer")
    ap.add_argument("--out", default="figures", help="Directorio de salida de las figuras")
    ap.add_argument("--dS", type=float, default=0.2, help="Ancho de capa radial [m]")
    ap.add_argument("--all", action="store_true", help="Genera todos los gráficos (1.1 a 1.4)")
    ap.add_argument("--timing", action="store_true")
    ap.add_argument("--cfc", action="store_true")
    ap.add_argument("--fu", action="store_true")
    ap.add_argument("--radial", action="store_true")
    ap.add_argument("--reg-tmin", type=float, default=0.0,
                    help="t mínimo para el ajuste lineal de Cfc(t)")
    ap.add_argument("--reg-tmax", type=float, default=None,
                    help="t máximo para el ajuste lineal de Cfc(t) (default: fin)")
    ap.add_argument("--fu-ymax", type=float, default=0.4,
                    help="Tope del eje y para Fu(t) (default: 0.4)")
    args = ap.parse_args()

    p = Plotter(sim_dir=args.sim_dir, results_dir=args.results_dir,
                out_dir=args.out, dS=args.dS)

    any_flag = args.timing or args.cfc or args.fu or args.radial
    if args.all or not any_flag:
        p.plot_all(regression_window=(args.reg_tmin, args.reg_tmax),
                   fu_ymax=args.fu_ymax)
        return

    if args.timing:
        p.plot_execution_time()
    if args.cfc:
        p.plot_cfc_and_J(regression_window=(args.reg_tmin, args.reg_tmax))
    if args.fu:
        p.plot_fu(y_max=args.fu_ymax)
    if args.radial:
        p.plot_radial_profiles()


if __name__ == "__main__":
    _main()