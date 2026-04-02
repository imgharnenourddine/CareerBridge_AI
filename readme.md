# 🤖 AI Career Transition Platform (Future Skills Guild)

## 📌 Overview

This project is an **AI-powered digital platform** designed to help workers affected by automation transition into new careers.

The system simulates an **interactive AI interview**, analyzes user skills, and provides personalized recommendations for training and job opportunities.

The goal is to create a **complete digital ecosystem** that guides users from their current job to a new professional path.

---

## 🎯 Problem Statement

A local factory is automating most of its production line, which leads to:
- Job losses for many workers
- Need for reskilling and career transition
- Lack of guidance for affected employees

---

## 💡 Proposed Solution

A **digital AI platform** that:
- Conducts an interactive voice-based interview
- Analyzes user skills using AI
- Recommends suitable career paths
- Suggests training programs
- Simulates job application and hiring process

---

## 🚀 Key Features

### 🎤 AI Voice Interview
- Users interact with an AI agent using voice
- Questions are dynamically generated
- Conversation continues until analysis is complete

### 🧠 Skill Analysis
- AI evaluates responses
- Identifies strengths, weaknesses, and interests
- Generates a personalized profile

### 📊 Career Recommendations
- Suggests suitable sectors and job roles
- Provides ranked recommendations

### 🎓 Training Suggestions
- Recommends relevant courses or learning paths
- Users can select a training program (simulation)

### 💼 Job Matching (Simulation)
- Displays companies with job opportunities
- Shows contact information (simulated)
- Allows users to apply or request AI-assisted application

### 💬 Interactive Messaging System
- Users receive results in an interactive inbox-style UI
- AI continues guiding the user through clickable actions (no manual typing required)

---

## 🧠 Innovation

- 🤖 AI-powered interview agent
- 🎤 Voice-based interaction (Speech-to-Text + Text-to-Speech)
- 🎯 Personalized career recommendations
- 🧭 End-to-end career transition workflow
- 🏫 "Guild" concept: a modern digital apprenticeship ecosystem
- 💬 Fully interactive user experience

---

## 🏗️ System Architecture

### Frontend (React)
- User interface
- Voice interaction handling
- Chat/message UI
- Display of AI responses and recommendations

### Backend (Spring Boot)
- REST APIs
- Business logic
- Communication with AI services
- User/session management

### AI Layer
- LLM integration using:
  - LangChain4j
  - Mistral AI (or other LLMs)
- Handles:
  - Interview generation
  - Response analysis
  - Recommendations

---

## 🎤 Speech Technologies

### Speech-to-Text (Voice → Text)
- Primary: **Web Speech API (Browser-based)**
- Fallback options:
  - Whisper (Open-source / local / API)
  - Vosk (Offline speech recognition)

### Text-to-Speech (Text → Voice)
- Primary: **Web Speech API (SpeechSynthesis)**
- Optional external APIs (for better voice quality):
  - eidosSpeech API
  - Other TTS services with free tiers

---

## 🔄 Workflow

1. User accesses the platform
2. Starts AI voice interview
3. Speech is converted to text
4. LLM analyzes responses
5. AI generates personalized insights
6. User receives results in interactive UI
7. User selects career interests
8. Platform suggests training programs
9. User selects and simulates enrollment
10. Platform suggests job opportunities
11. User applies (simulation)
12. AI follows up with simulated hiring process

---

## 🧪 Simulation Aspects

To keep the project academic and realistic:
- Email sending is simulated
- Payment process is simulated
- Company responses (accept/reject) are simulated
- Job applications are simulated

---

## 🛠️ Technologies Used

### Frontend
- React.js
- JavaScript (ES6+)
- Web Speech API

### Backend
- Spring Boot
- REST APIs

### AI & LLM
- LangChain4j
- Mistral AI (or compatible LLM)

### Speech Processing
- Web Speech API (primary)
- Whisper / Vosk (fallback options)

---

## 📊 System Concept (Summary Flow)

Worker → Voice Interview → Speech-to-Text → LLM Analysis → Recommendations → Training Selection → Job Suggestions → Application → Simulation of Hiring

---

## 🧾 Objectives

- Help workers adapt to automation changes
- Provide an intelligent and guided career transition system
- Demonstrate integration of AI into real-world social problems
- Build an end-to-end digital ecosystem combining AI, web development, and user interaction

---

## 🏁 Conclusion

This project is not just a platform, but a **digital transformation ecosystem** that acts as a modern "Guild", guiding workers through a complete journey of reskilling and reintegration into the workforce using artificial intelligence.

---