#!/usr/bin/env python3
"""Run phishing RF model on 5 calibrated samples and print one probability per line."""
import sys
import warnings
import joblib

# 5 calibrated samples spanning the model's output range (~63% to ~82%)
# All un-specified features default to 0.
SAMPLES = [
    # Sample 0: ~63% — near-benign (HTTP site that submits to email, atypical)
    {
        "NoHttps": 0,
        "SubmitInfoToEmail": 1,
    },
    # Sample 1: ~69% — mild indicators (domain mismatch + submit to email)
    {
        "NoHttps": 0,
        "SubmitInfoToEmail": 1,
        "FrequentDomainNameMismatch": 1,
    },
    # Sample 2: ~71% — moderate (baseline, minimal attack features)
    {},
    # Sample 3: ~77% — strong indicators (original attack sample)
    {
        "PctExtHyperlinks": 0.85,
        "PctExtNullSelfRedirectHyperlinksRT": 0.90,
        "FrequentDomainNameMismatch": 1,
        "PctExtResourceUrls": 0.80,
        "PctNullSelfRedirectHyperlinks": 0.70,
        "NumDash": 5,
        "ExtMetaScriptLinkRT": 0.75,
        "SubmitInfoToEmail": 1,
        "InsecureForms": 1,
    },
    # Sample 4: ~82% — max attack (all key indicators + structure flags)
    {
        "FrequentDomainNameMismatch": 1,
        "MissingTitle": 1,
        "PathLevel": 5,
        "NumDash": 10,
        "PctNullSelfRedirectHyperlinks": 1,
        "PctExtHyperlinks": 1,
        "ExtFavicon": 1,
    },
]


def main():
    try:
        model = joblib.load("Phishing_Agent/phishing_model.pkl")

        try:
            feature_names = list(model.feature_names_in_)
        except AttributeError:
            feature_names = list(SAMPLES[3].keys())

        classes = list(model.classes_)
        attack_idx = classes.index(1) if 1 in classes else len(classes) - 1

        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            for sample in SAMPLES:
                X = [[sample.get(f, 0) for f in feature_names]]
                proba = model.predict_proba(X)[0]
                print(proba[attack_idx])

    except Exception as e:
        print(f"[predict_phishing] error: {e}", file=sys.stderr)
        for _ in SAMPLES:
            print(0.0)


if __name__ == "__main__":
    main()
