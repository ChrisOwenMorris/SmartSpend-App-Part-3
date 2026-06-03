SmartSpend – Personal Budgeting Android Application (POE Part 3)
Group Project Overview
SmartSpend is a mobile budgeting application developed in Android Studio to help users manage their personal finances effectively. The app allows users to track income, expenses, savings goals, and spending habits through a modern and user-friendly interface.

This project was completed in three development phases:

Phase 1: User Interface Design (Layouts / Front-End)

Phase 2: Functionality Implementation (Back-End / Logic / Database)

Phase 3: Advanced Feature Integration (Custom Functionality)

The application was developed collaboratively using GitHub branches and version control.

Project Objective
The goal of SmartSpend is to provide users with an easy-to-use finance management tool that enables them to:

Track income and expenses

Create and manage savings goals

View reports and spending summaries

Monitor financial habits

Manage transactions offline using local storage

🚀 Key Custom Features (Part 3)
To provide a more comprehensive financial management experience, we implemented the following custom features:

Enhanced Receipt Management: We integrated a robust receipt management system that allows users to attach digital receipts to their financial records. Users can link receipts directly to existing transactions or upload new ones at the moment of entry. This uses ActivityResultContracts and ImageView components to verify expenses securely.

Custom Downloadable Reporting: We expanded the reporting functionality to provide users with tangible insights into their spending habits. Users can generate and download comprehensive monthly or yearly reports. These reports aggregate spending data into clear, easy-to-read formats, helping users monitor their progress toward financial goals and identify areas for budget adjustments.

Technologies Used
Android Studio

Kotlin

XML Layouts

Room Database

RecyclerView

Material Design Components

GitHub Version Control

Development Phases
Phase 1 – Layout Design
During Phase 1, all screens were designed and structured using XML layouts. Focus was placed on user experience, navigation, consistency, and visual appeal.

Phase 2 – Functionality Implementation
During Phase 2, all app logic and back-end features were integrated using Kotlin and Room Database, including user registration, goal creation, and transaction tracking.

Phase 3 – Advanced Feature Integration
During Phase 3, we focused on enhancing usability through the Receipt Management system and the Downloadable Report feature.

Team Contributions
Note: All team members refined their individual screens throughout the project, incorporating Part 2 feedback before beginning Part 3.

Group Leader – Christopher Morris
Final UI enhancements

App testing

GitHub repository creation

YouTube video production

Team coordination

Member 1 – Janaid Shaik
Firebase database integration

Unit testing

Code commenting

Academic referencing

GitHub repository management

Member 2 – Thato Guemede
Settings page development

Settings functionality updates

Maintenance of analytics logic

Reports screen refinement

Member 3 – Andisa Azobe
Full Reports page development

Working graph integration

Dynamic chart implementation

Transaction tracking maintenance

Member 4 – Reesaido Alagiry
Downloadable report feature

PDF/Data export logic

Goals screen maintenance

Savings progress tracking

Database Architecture: Hybrid Storage
To ensure SmartSpend provides a seamless user experience regardless of connectivity, we implemented a dual-layer database architecture:

Room Database (Local/Offline): Acts as the primary source of truth for immediate data access, ensuring the app remains fully functional, fast, and responsive without an active internet connection.

Firebase (Online/Sync): Provides cloud-based synchronization and real-time data backup, allowing users to keep their financial data consistent across multiple devices.

This hybrid approach ensures that data entered while offline is automatically synced to the cloud once a connection is restored, providing the best of both local performance and cloud reliability.

GitHub Workflow Used
The team used GitHub branches to ensure efficient collaboration, manual conflict resolution, and consistent testing.

How to Run the Project
Clone repository

Open in Android Studio

Sync Gradle files

Run on Emulator / Android Device

Launch SmartSpend

Conclusion
SmartSpend successfully demonstrates teamwork, Android development principles, UI/UX design, local database management, and collaborative software engineering. The project met all Phase 1, 2, and 3 requirements.

YouTube Link


Authors
Developed by Group Members
IIE Varsity College
