"""
Kien truc (QUEST_AI_PLAN.md muc 4.2):

    Backbone: MobileNetV3-Small, pretrained ImageNet, input 224x224
              -> global avg pool -> 576-dim   (torchvision .features + avgpool)
    Head:     Linear(576->256) -> ReLU -> Dropout(0.2) -> Linear(256->12)   [~150k params]
    Loss:     BCEWithLogitsLoss(pos_weight per-class)   (multi-label, KHONG softmax)

  v0: backbone dong bang, chi train head (tu cache embedding — vai chuc giay).
  v1: mo block conv cuoi cua backbone (LR x0.1) + head, co augmentation.
"""
from __future__ import annotations

import torch
import torch.nn as nn
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

from .classes import NUM_CLASSES

EMBED_DIM = 576  # output cua mobilenet_v3_small.features sau avgpool


class Head(nn.Module):
    """Head multi-label — phan DUY NHAT train o v0."""

    def __init__(self, in_dim: int = EMBED_DIM, hidden: int = 256, num_classes: int = NUM_CLASSES, dropout: float = 0.2):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(in_dim, hidden),
            nn.ReLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(hidden, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:  # (N, 576) -> (N, 12) logits
        return self.net(x)


def build_backbone(pretrained: bool = True) -> nn.Module:
    """MobileNetV3-Small features + avgpool -> vector 576 (bo classifier ImageNet)."""
    weights = MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
    m = mobilenet_v3_small(weights=weights)
    return nn.Sequential(m.features, m.avgpool, nn.Flatten(1))


class SnapgetClassifier(nn.Module):
    """Backbone + head — dung de fine-tune v1 va de export ONNX (input NCHW da chuan hoa)."""

    def __init__(self, backbone: nn.Module, head: Head):
        super().__init__()
        self.backbone = backbone
        self.head = head

    def forward(self, x: torch.Tensor) -> torch.Tensor:  # (N,3,224,224) -> (N,12) logits
        return self.head(self.backbone(x))

    def freeze_backbone(self) -> None:
        for p in self.backbone.parameters():
            p.requires_grad = False

    def unfreeze_last_block(self) -> None:
        """v1: mo block conv cuoi (features[-1] = Conv 96->576 + BN + Hardswish) — phan con lai van dong bang."""
        self.freeze_backbone()
        features = self.backbone[0]
        for p in features[-1].parameters():
            p.requires_grad = True
        for p in features[-2].parameters():  # them 1 InvertedResidual cuoi cho co "chat" hon
            p.requires_grad = True


def build_model(pretrained: bool = True, head_state: dict | None = None) -> SnapgetClassifier:
    model = SnapgetClassifier(build_backbone(pretrained), Head())
    if head_state is not None:
        model.head.load_state_dict(head_state)
    return model
