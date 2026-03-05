package com.example.edulock;

public class FaqItem {
    private String question;
    private String answer;
    private boolean expanded;

    public FaqItem(String question, String answer) {
        this.question = question;
        this.answer = answer;
        this.expanded = false;
    }

    // Getters and setters
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
}
