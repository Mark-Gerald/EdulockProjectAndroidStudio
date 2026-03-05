package com.example.edulock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FaqAdapter extends RecyclerView.Adapter<FaqAdapter.FaqViewHolder> {
    private List<FaqItem> faqItems;

    public FaqAdapter(List<FaqItem> faqItems) {
        this.faqItems = faqItems;
    }

    @NonNull
    @Override
    public FaqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_faq, parent, false);
        return new FaqViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FaqViewHolder holder, int position) {
        FaqItem item = faqItems.get(position);
        holder.questionText.setText(item.getQuestion());
        holder.answerText.setText(item.getAnswer());

        holder.answerText.setVisibility(item.isExpanded() ? View.VISIBLE : View.GONE);
        holder.arrowImage.setRotation(item.isExpanded() ? 180 : 0);

        holder.itemView.setOnClickListener(v -> {
            item.setExpanded(!item.isExpanded());
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return faqItems.size();
    }

    static class FaqViewHolder extends RecyclerView.ViewHolder {
        TextView questionText;
        TextView answerText;
        ImageView arrowImage;

        FaqViewHolder(View itemView) {
            super(itemView);
            questionText = itemView.findViewById(R.id.questionText);
            answerText = itemView.findViewById(R.id.answerText);
            arrowImage = itemView.findViewById(R.id.arrowImage);
        }
    }
}
