# Stock Shark - Android Inventory Management

A comprehensive Android inventory management application built for small businesses, warehouses, and individual users who need efficient stock level tracking and management.

## Overview

Stock Shark provides an intuitive mobile solution for inventory management challenges faced by small to medium-sized operations. The application combines secure user authentication, real-time inventory tracking, and a modern Material Design interface to deliver a professional-grade inventory management experience on Android devices.

## Features

### Inventory Management
- **Comprehensive Item Tracking**: Add, edit, delete, and search inventory items with detailed categorization
- **Real-Time Stock Updates**: Instant quantity adjustments with automatic level calculations
- **Barcode Integration**: Scan-to-add functionality for rapid inventory entry
- **Low Stock Alerts**: Configurable notifications for items below minimum thresholds
- **Advanced Search & Filtering**: Multi-criteria search with category-based filtering

### Security & Data Protection
- **Secure Authentication**: SHA-256 password hashing with unique salt generation
- **SQL Injection Prevention**: Parameterized queries and sanitized input handling
- **Input Validation**: Comprehensive client-side validation with error prevention
- **Session Management**: Secure authentication state with automatic timeout

### User Experience
- **Material Design Interface**: Modern UI following Google's design guidelines
- **Accessibility Support**: Screen reader compatibility and keyboard navigation
- **Dark Mode Support**: System-adaptive theming for user preference
- **Offline Functionality**: Full feature access without internet connectivity
- **Responsive Layout**: Optimized for various Android screen sizes

### Technical Performance
- **Optimized Database**: SQLite with indexing for large inventory datasets
- **Memory Efficient**: Optimized RecyclerView implementation with ViewHolder pattern
- **Background Processing**: AsyncTask implementation for smooth UI performance
- **Data Export**: CSV export functionality for external reporting

## Architecture

### Technology Stack
- **Language**: Java 8+
- **Platform**: Android SDK (API 24+ to 34)
- **Database**: SQLite with Room persistence library
- **UI Framework**: Material Design Components 1.9+
- **Architecture Pattern**: MVP (Model-View-Presenter) with Repository pattern
- **Build System**: Gradle with Android Gradle Plugin

## Installation and Setup

### Prerequisites
- **Android Studio**: Arctic Fox (2020.3.1) or newer
- **Android SDK**: API levels 24-34
- **Java Development Kit**: JDK 8 or higher (bundled with Android Studio)

### Development Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Select target device/emulator → Run 'app'

