# model.py
import torch
import torch.nn as nn

class GazeLSTMClassifier(nn.Module):
    def __init__(self, input_size=2, hidden_size=64, num_classes=2):
        super().__init__()
        self.lstm = nn.LSTM(input_size, hidden_size, batch_first=True)
        self.fc = nn.Linear(hidden_size, num_classes)

    def forward(self, x):
        _, (hn, _) = self.lstm(x)
        out = self.fc(hn.squeeze(0))
        return out
