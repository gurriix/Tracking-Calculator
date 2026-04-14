# Android Tracking Calculator

A seemingly standard calculator application designed for Android that incorporates underlying tracking functionalities. This project serves as a security research tool to demonstrate the fine line between utility and data collection.

## Project Overview

The application maintains the appearance of a routine utility, providing a fully functional interface for arithmetic operations. However, it includes a dedicated visualization portal accessible via a specific button that displays real-time data and locators being collected from the device.

## Research Objectives

This project was conceived to demonstrate the ease with which large-scale developers can collect anonymous user data. By aggregating a specific set of identifiers, it is possible to:
* **Create Unique User Profiles:** Utilize device fingerprinting to distinguish individual users without traditional PII.
* **Advanced Traceability:** Enable persistent tracking across different sessions and environments.
* **Transparency Awareness:** Highlight how seemingly harmless metadata can be combined to bypass privacy expectations.

## Collected Identifiers (Fingerprinting Data)

The following data points are captured to showcase the potential for unique digital footprinting:

* **System & Hardware:**
    * `boot_time`: The exact time since the last system restart.
    * `device_model` & `manufacturer`: Specific hardware identification.
    * `screen_width` & `screen_height`: Physical display dimensions.
* **Software & OS:**
    * `android_version`: Operating system version.
    * `build_fingerprint`: Unique build identifier for the specific device ROM.
    * `kernel_version`: System kernel information.
* **Environment & Connectivity:**
    * `timezone`: Regional settings and location indicators.
    * `wifi_networks`: List of nearby wireless networks.
    * `bluetooth_devices`: Visible peripheral devices in the vicinity.

## Ethical Alignment & Principles

In alignment with world-class engineering standards, this project is guided by the following principles:

* [cite_start]**Responsibility & Transparency:** Following the "Freedom and Responsibility" model [cite: 101][cite_start], this tool aims to provide "constructive feedback" to the development community regarding the use of user resources[cite: 20].
* [cite_start]**Resource Efficiency:** The tracking engine is designed to be lean, ensuring it does not interfere with the primary utility, reflecting the principle of acting in the best interest of the system's performance[cite: 8].
* **Security Advocacy:** By exposing these methods, the goal is to drive a shift toward **Secure Development Lifecycles (SDLC)** and more rigorous data protection standards.

---
**Disclaimer:** This project is for educational and security research purposes only. It is intended to help developers and users understand the mechanics of mobile tracking and to foster more private and secure application environments.
