#!/usr/bin/env python3
"""Run brute-force RF model on 5 calibrated samples and print one probability per line."""
import sys
import warnings
import joblib

# CICIDS-2017 standard feature names (well-documented public dataset)
CICIDS_FEATURES = [
    "Destination Port", "Flow Duration", "Total Fwd Packets",
    "Total Backward Packets", "Total Length of Fwd Packets",
    "Total Length of Bwd Packets", "Fwd Packet Length Max",
    "Fwd Packet Length Min", "Fwd Packet Length Mean", "Fwd Packet Length Std",
    "Bwd Packet Length Max", "Bwd Packet Length Min", "Bwd Packet Length Mean",
    "Bwd Packet Length Std", "Flow Bytes/s", "Flow Packets/s",
    "Flow IAT Mean", "Flow IAT Std", "Flow IAT Max", "Flow IAT Min",
    "Fwd IAT Total", "Fwd IAT Mean", "Fwd IAT Std", "Fwd IAT Max", "Fwd IAT Min",
    "Bwd IAT Total", "Bwd IAT Mean", "Bwd IAT Std", "Bwd IAT Max", "Bwd IAT Min",
    "Fwd PSH Flags", "Bwd PSH Flags", "Fwd URG Flags", "Bwd URG Flags",
    "Fwd Header Length", "Bwd Header Length", "Fwd Packets/s", "Bwd Packets/s",
    "Min Packet Length", "Max Packet Length", "Packet Length Mean",
    "Packet Length Std", "Packet Length Variance", "FIN Flag Count",
    "SYN Flag Count", "RST Flag Count", "PSH Flag Count", "ACK Flag Count",
    "URG Flag Count", "CWE Flag Count", "ECE Flag Count", "Down/Up Ratio",
    "Average Packet Size", "Avg Fwd Segment Size", "Avg Bwd Segment Size",
    "Fwd Header Length.1", "Subflow Fwd Packets", "Subflow Fwd Bytes",
    "Subflow Bwd Packets", "Subflow Bwd Bytes", "Init_Win_bytes_forward",
    "Init_Win_bytes_backward", "act_data_pkt_fwd", "min_seg_size_forward",
    "Active Mean", "Active Std", "Active Max", "Active Min",
    "Idle Mean", "Idle Std", "Idle Max", "Idle Min",
]

# 5 calibrated samples spanning the model's range (~13% to ~68%)
# The brute-force model's key discriminating features are Fwd Header Length,
# min_seg_size_forward, Fwd Packet Length Mean, Packet Length Std, Flow Bytes/s,
# Fwd IAT Mean, Fwd IAT Max, and Packet Length Variance.
SAMPLES = [
    # Sample 0: ~13% — normal traffic (all zeros / default values)
    {},
    # Sample 1: ~33% — mild indicators (slightly elevated header lengths)
    {
        "Fwd Header Length": 50,
        "Fwd Header Length.1": 50,
        "min_seg_size_forward": 20,
    },
    # Sample 2: ~53% — moderate (characteristic header + segment sizes)
    {
        "Fwd Header Length": 100,
        "Fwd Header Length.1": 100,
        "min_seg_size_forward": 30,
        "Fwd Packet Length Mean": 25,
        "Packet Length Std": 50,
    },
    # Sample 3: ~59% — strong brute-force indicators
    {
        "Fwd Header Length": 200,
        "Fwd Header Length.1": 200,
        "min_seg_size_forward": 50,
        "Fwd Packet Length Mean": 50,
        "Packet Length Std": 50,
        "Flow Bytes/s": 25,
    },
    # Sample 4: ~68% — extreme indicators (max attack combo)
    {
        "Fwd Header Length": 200,
        "Fwd Header Length.1": 200,
        "min_seg_size_forward": 50,
        "Fwd Packet Length Mean": 50,
        "Packet Length Std": 50,
        "Flow Bytes/s": 25,
        "Fwd IAT Mean": 100,
        "Fwd IAT Max": 25,
        "Total Length of Fwd Packets": 10,
        "Packet Length Variance": 10,
        "Max Packet Length": 10,
        "Subflow Fwd Packets": 10,
    },
]


def main():
    try:
        model = joblib.load("Brute_Force Agent/Brute_force_model.pkl")

        try:
            feature_names = list(model.feature_names_in_)
        except AttributeError:
            n = model.n_features_in_ if hasattr(model, "n_features_in_") else len(CICIDS_FEATURES)
            feature_names = CICIDS_FEATURES[:n]

        classes = list(model.classes_)
        # Find attack class — look for non-BENIGN label
        attack_idx = 0
        for i, c in enumerate(classes):
            label = str(c).upper()
            if "BENIGN" not in label and label not in ("0", "NORMAL"):
                attack_idx = i
                break

        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            for sample in SAMPLES:
                X = [[sample.get(f, 0.0) for f in feature_names]]
                proba = model.predict_proba(X)[0]
                print(proba[attack_idx])

    except Exception as e:
        print(f"[predict_bruteforce] error: {e}", file=sys.stderr)
        for _ in SAMPLES:
            print(0.0)


if __name__ == "__main__":
    main()
