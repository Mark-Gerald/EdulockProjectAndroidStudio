package com.example.edulock;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class FactsFragment extends Fragment {
    private RecyclerView faqRecyclerView;
    private List<FaqItem> faqItems;

    public FactsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_facts, container, false);

        faqRecyclerView = view.findViewById(R.id.faqRecyclerView);
        faqRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        initializeFaqItems();
        FaqAdapter adapter = new FaqAdapter(faqItems);
        faqRecyclerView.setAdapter(adapter);

        return view;
    }

    private void initializeFaqItems() {
        faqItems = new ArrayList<>();
        //1
         faqItems.add(new FaqItem(
                "What does the app do?",
                "EduLock is a screen time management and restriction tool designed to help users monitor and control their device usage. \n\n" +
                        "It provides insights into daily screen time, tracks app usage, and allows users to set restrictions on specific apps based on schedules or session limits. \n\n" +
                        "This app include features: \n\n" +
                        "App Statistics: Displays a breakdown of time spent on different apps. It shows statistics on which apps are used the most. \n\n" +
                        "App Control: Users can choose a restriction type, align it with monitoring goals, and adjust or remove it through the control panel. \n\n" +
                        "App Restrict: Direct Restriction screen where users can block apps immediately by click on switches for apps like Facebook, Instagram, TikTok, Messenger, and more."
        ));
         //2
        faqItems.add(new FaqItem(
                "How do I set up the restrictions?",
                "Go to the Restrict Section. You can navigate this by clicking the Restrict in the bottom navigation.\n\n" +
                        "To be able to restrict. The teacher and student device needs to connect by via QR code."
        ));
        //3
        faqItems.add(new FaqItem(
                 "Is my data safe with the restrictions app?",
                 "Yes! EduLock requires usage access permission to function effectively. By granting this permission, EduLock can offer detailed insights into your app usage patterns, helping you monitor and manage screen time efficiently."
        ));
        //4
        faqItems.add(new FaqItem(
                "What to do for tech issues?",
                "Any issues in the app. You can contact the developers in the Contact Us section."
        ));
        //5
        faqItems.add(new FaqItem(
                "Can I limit access to specific apps?",
                "YES! You can do this in the Control Section. You can navigate the Control Section in the bottom navigation buttons. Click the one only button in the fragment and choose your time and the app/s you want to limit time usage."
        ));
        //6
        faqItems.add(new FaqItem(
                "Does the app work offline?",
                "No, EduLock needs an internet connection for certain features to function properly. If a student and teacher controls restrictions from another device, EduLock requires an internet connection to receive and apply the change."
        ));
        //7
        faqItems.add(new FaqItem(
                "How to set other device restrictions?",
                "If you're a Teacher You Need to Connect your phone to your student's phone via internet to monitor their usage, track app activities,and set restrictions for specific apps or time periods."
        ));
        //8
        faqItems.add(new FaqItem(
                "Do I need to sign in to use it?",
                "Yes, you must sign in to use EduLock. EduLock is a school-related platform from Lagro High School that helps manage student and teacher access to apps and devices."
        ));
        //9
        faqItems.add(new FaqItem(
                "Are there age limits for the app?",
                "Edulock has NO age restrictions, allowing any student to register, regardless of their age. As a result, students can use the app without needing parental consent, which raises privacy issues. While teachers and students can manage app restrictions, the features available may vary."
        ));
        // Add more items as needed
    }
}