OJTTrack — Minimum Viable Product (MVP)



1\. Project Overview



OJTTrack is a web-based application designed to help students manage their On-the-Job Training (OJT) applications, placements, and training hours. It also enables school OJT coordinators to manage student accounts, monitor training progress, and verify submitted time logs.



The MVP focuses on providing a centralized and organized way to manage the essential stages of the OJT process, from student account creation to the verification of completed training hours.



2\. Target Users



Students



Students use OJTTrack to manage company applications, view their OJT placement details, record daily attendance and accomplishments, and monitor their verified training hours.



OJT Coordinators



OJT coordinators use OJTTrack to create student accounts, monitor application progress, establish OJT placements for accepted applications, and review and verify student time logs.



3\. Core User Workflow



1\. The coordinator creates a student account and provides the student with login access.

2\. The student logs in and records the companies they plan to apply to.

3\. The student updates each application as they apply, attend interviews, or receive a decision.

4\. The coordinator confirms that the student has an accepted application before creating an OJT placement with the company, role, training dates, and required hours.

5\. The student checks their placement details and records their daily attendance, working hours, and accomplishments.

6\. The coordinator reviews each submitted time log and either verifies it or returns it for correction.

7\. The student corrects and resubmits any returned time logs.

8\. The coordinator verifies the corrected logs, and OJTTrack updates the student's total verified hours and remaining hours.



4\. Core Features



4.1 Account and Access Management



\- Coordinators can create student accounts.

\- Students and coordinators can log in using their assigned credentials.

\- Access is restricted according to the user's role.

\- Students can access only their own records.

\- Passwords are stored securely using password hashing.



4.2 Company Application Tracking



\- Students can create, view, update, and delete their application records.

\- Each application records the company name and current application status.

\- Students can track statuses such as Planned, Applied, Interview, Offered, Accepted, Rejected, and Withdrawn.

\- Students can search and filter their applications.

\- Application records remain available after the student logs out or refreshes the page.



4.3 OJT Placement Management



\- Coordinators can create an OJT placement only when the student's application has been marked as Accepted.

\- Each placement records the student, company, role, training start and end dates, required training hours, and placement status.

\- Students can view their placement details.

\- An accepted application does not automatically create a placement or indicate that training has started.



4.4 Daily Time Log Management



\- Students can submit daily time logs containing their attendance times, break duration, and accomplishments.

\- OJTTrack calculates the total working hours on the server.

\- Each time log has a status of Pending, Verified, or Returned.

\- Coordinators can review submitted logs, verify them, or return them with feedback for correction.

\- Students can correct and resubmit returned logs.

\- Students cannot verify their own time logs.

\- The system records who verified a log and when the verification occurred.



4.5 Student Dashboard



\- Displays application statuses and relevant follow-ups.

\- Shows the student's current OJT placement.

\- Displays required, verified, and remaining training hours.

\- Identifies pending and returned time logs.



4.6 Coordinator Dashboard



\- Displays student accounts and active OJT placements.

\- Provides an overview of student training progress.

\- Highlights time logs awaiting review.

\- Helps coordinators identify students who may need assistance.



4.7 School Requirements and Tasks



\- Students can view assigned school requirements or OJT-related tasks.

\- Students can track their progress on these tasks.

\- Coordinators can manage relevant requirements and monitor student progress.



4.8 User Interface and Data Validation



\- Provides a responsive interface for desktop and mobile screens.

\- Includes loading, empty, success, and error states.

\- Validates user input before saving records.

\- Displays clear messages when an action fails or requires correction.



5\. Core Business Rules



1\. Only authenticated users can access protected areas of the application.

2\. User permissions are enforced on the server, not only in the user interface.

3\. Students can view and manage only the records they are authorized to access.

4\. A placement can be created only after the corresponding application is accepted.

5\. Coordinators are responsible for verifying student time logs.

6\. Only verified time logs contribute to a student's completed OJT hours.

7\. Training hours are calculated by the backend using the submitted attendance and break information.

8\. Returned time logs must be corrected and resubmitted before they can be verified.

9\. The system must preserve saved records across sessions and page refreshes.



6\. Features Excluded from the MVP



The following features are intentionally excluded from the initial release to keep development focused on the core OJT workflow.



Automated Email Notifications



The system will not send automated emails for application updates, placement creation, or time log reviews. Users can still access the relevant information by logging in to OJTTrack.



Document Uploads



The system will not store uploaded application documents, school requirements, or other attachments. The core workflow can operate using structured records and time log information.



7\. MVP Completion Criteria



The MVP will be considered functional when:



\- A coordinator can create a student account and the student can log in.

\- A student can create and update company applications.

\- A coordinator can create a placement for an accepted application.

\- A student can submit daily time logs.

\- A coordinator can verify or return submitted logs.

\- A student can correct and resubmit returned logs.

\- The system calculates and displays verified and remaining OJT hours accurately.

\- Role-based access controls prevent unauthorized access to records.

\- Core data persists in the database and remains available after refreshing the application.

\- The primary workflow works correctly across the frontend, backend, and database.



8\. Technology Stack



Frontend: React, JavaScript, HTML, CSS, and Vite



Backend: Java and Spring Boot



Database: PostgreSQL



Database Migrations: Flyway



Build Tool: Apache Maven



Version Control: Git and GitHub



9\. Development Principle



OJTTrack will be developed incrementally, prioritizing a working and secure core workflow over additional features. Each feature should be implemented, tested, and understood before moving to the next stage. The initial release should remain simple enough to maintain while providing a reliable foundation for future improvements.

