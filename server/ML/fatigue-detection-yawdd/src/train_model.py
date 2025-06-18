import os
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import classification_report, confusion_matrix
import matplotlib.pyplot as plt
import seaborn as sns
import joblib

# === Конфигурация ===
DATA_DIR = "../data/fatigue_dataset"
LABEL_FILE = "../data/labels.csv"
EPOCHS = 100
BATCH_SIZE = 16
MAX_SEQ_LEN = 35
PATIENCE = 10

# === Dataset ===
class FatigueDataset(Dataset):
    def __init__(self, files, labels, max_seq_len=MAX_SEQ_LEN):
        self.files = files
        self.labels = labels
        self.max_seq_len = max_seq_len

    def __len__(self):
        return len(self.files)

    def __getitem__(self, idx):
        features = np.load(self.files[idx]).astype(np.float32)
        features = (features - np.mean(features)) / (np.std(features) + 1e-5)

        if features.ndim == 3 and features.shape[2] == 2:
            features = features.reshape(features.shape[0], -1)

        seq_len = features.shape[0]
        feature_dim = features.shape[1]

        if seq_len > self.max_seq_len:
            features = features[:self.max_seq_len]
        else:
            padding = np.zeros((self.max_seq_len - seq_len, feature_dim), dtype=np.float32)
            features = np.vstack((features, padding))

        return torch.tensor(features), torch.tensor(self.labels[idx])

# === Model ===
class CNNLSTM(nn.Module):
    def __init__(self, input_size, num_classes):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv1d(input_size, 128, kernel_size=3, padding=1),
            nn.BatchNorm1d(128),
            nn.ReLU(),
            nn.MaxPool1d(2),
            nn.Conv1d(128, 64, kernel_size=3, padding=1),
            nn.BatchNorm1d(64),
            nn.ReLU(),
            nn.MaxPool1d(2),
        )
        self.lstm = nn.LSTM(input_size=64, hidden_size=64, batch_first=True)
        self.fc = nn.Linear(64, num_classes)

    def forward(self, x):
        x = x.permute(0, 2, 1)  # [B, features, seq_len]
        x = self.cnn(x)
        x = x.permute(0, 2, 1)  # [B, seq_len, features]
        _, (h_n, _) = self.lstm(x)
        return self.fc(h_n[-1])

# === Загрузка данных ===
def load_data():
    files, labels = [], []
    with open(LABEL_FILE, 'r') as f:
        next(f)
        for line in f:
            fname, label = line.strip().split(',')
            path = os.path.join(DATA_DIR, fname)
            if os.path.exists(path):
                files.append(path)
                labels.append(label)
    return files, labels

# === Обучение ===
def train():
    files, labels = load_data()
    le = LabelEncoder()
    y = le.fit_transform(labels)
    X_train, X_val, y_train, y_val = train_test_split(files, y, test_size=0.2, random_state=42)

    # Сохраняем LabelEncoder
    joblib.dump(le, "label_encoder.joblib")

    sample = np.load(X_train[0])
    if sample.ndim == 3 and sample.shape[2] == 2:
        sample = sample.reshape(sample.shape[0], -1)
    input_size = sample.shape[1]
    num_classes = len(le.classes_)

    train_ds = FatigueDataset(X_train, y_train)
    val_ds = FatigueDataset(X_val, y_val)
    train_dl = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)
    val_dl = DataLoader(val_ds, batch_size=BATCH_SIZE)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = CNNLSTM(input_size, num_classes).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    criterion = nn.CrossEntropyLoss()

    best_val_loss = float('inf')
    trigger_times = 0
    train_losses, val_losses, val_accuracies = [], [], []

    for epoch in range(EPOCHS):
        model.train()
        total_loss, correct, total = 0, 0, 0
        for x, y in train_dl:
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            out = model(x)
            loss = criterion(out, y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            correct += (out.argmax(1) == y).sum().item()
            total += y.size(0)

        train_losses.append(total_loss)

        # === Валидация ===
        model.eval()
        val_loss, correct, total = 0, 0, 0
        all_preds, all_true = [], []
        with torch.no_grad():
            for x, y in val_dl:
                x, y = x.to(device), y.to(device)
                out = model(x)
                loss = criterion(out, y)
                val_loss += loss.item()
                preds = out.argmax(1)
                correct += (preds == y).sum().item()
                total += y.size(0)
                all_preds.extend(preds.cpu().numpy())
                all_true.extend(y.cpu().numpy())

        val_loss /= len(val_dl)
        acc = correct / total
        val_losses.append(val_loss)
        val_accuracies.append(acc)

        print(f"Epoch {epoch+1}: TrainLoss={total_loss:.4f} ValLoss={val_loss:.4f} ValAcc={acc:.4f}")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(), "best_model.pt")
            trigger_times = 0
        else:
            trigger_times += 1
            if trigger_times >= PATIENCE:
                print("Early stopping triggered")
                break

    # === Отчёт ===
    print("\nClassification Report:")
    print(classification_report(all_true, all_preds, target_names=le.classes_))

    # === Confusion Matrix ===
    conf_mat = confusion_matrix(all_true, all_preds)
    plt.figure(figsize=(8, 6))
    sns.heatmap(conf_mat, annot=True, fmt='d', xticklabels=le.classes_, yticklabels=le.classes_, cmap="Blues")
    plt.title("Confusion Matrix")
    plt.xlabel("Predicted")
    plt.ylabel("True")
    plt.tight_layout()
    plt.savefig("confusion_matrix.png")

    # === Loss plot ===
    plt.figure()
    plt.plot(train_losses, label="Train Loss")
    plt.plot(val_losses, label="Val Loss")
    plt.legend()
    plt.title("Loss over Epochs")
    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.savefig("loss_plot.png")

    # === Accuracy plot ===
    plt.figure()
    plt.plot(val_accuracies, label="Val Accuracy")
    plt.legend()
    plt.title("Validation Accuracy over Epochs")
    plt.xlabel("Epoch")
    plt.ylabel("Accuracy")
    plt.savefig("accuracy_plot.png")

if __name__ == "__main__":
    train()
