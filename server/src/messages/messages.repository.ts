import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { ChatGroup, Message } from './entities/message.entity';

/**
 * NOI DUY NHAT cham Firestore cho messages + chatGroups.
 * Giu nguyen tac 1-filter: thread 1-1 = 2 query equality theo senderId, merge trong bo nho.
 */
@Injectable()
export class MessagesRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get messages() {
    return this.firebase.firestore().collection(Collections.MESSAGES);
  }

  private get groups() {
    return this.firebase.firestore().collection(Collections.CHAT_GROUPS);
  }

  async createMessage(msg: Omit<Message, 'messageId'>): Promise<Message> {
    // Firestore khong nhan undefined -> loai field rong truoc khi ghi.
    const data = Object.fromEntries(Object.entries(msg).filter(([, v]) => v !== undefined));
    const ref = await this.messages.add(data);
    return { messageId: ref.id, ...msg };
  }

  async findById(messageId: string): Promise<Message | null> {
    const snap = await this.messages.doc(messageId).get();
    if (!snap.exists) {
      return null;
    }
    return this.toMessage(snap.id, snap.data() ?? {});
  }

  /** Thread 1-1 giua a va b: (sender=a & receiver=b) + (sender=b & receiver=a). */
  async listBetween(a: string, b: string): Promise<Message[]> {
    const [sentSnap, receivedSnap] = await Promise.all([
      this.messages.where('senderId', '==', a).get(),
      this.messages.where('senderId', '==', b).get(),
    ]);
    const sent = sentSnap.docs
      .map((d) => this.toMessage(d.id, d.data()))
      .filter((m) => m.receiverId === b);
    const received = receivedSnap.docs
      .map((d) => this.toMessage(d.id, d.data()))
      .filter((m) => m.receiverId === a);
    return [...sent, ...received].sort((x, y) => x.sendTime.localeCompare(y.sendTime));
  }

  /** Moi tin nhan 1-1 lien quan toi uid (gui + nhan) — phuc vu danh sach hoi thoai. */
  async listInvolving(uid: string): Promise<Message[]> {
    const [sentSnap, receivedSnap] = await Promise.all([
      this.messages.where('senderId', '==', uid).get(),
      this.messages.where('receiverId', '==', uid).get(),
    ]);
    return [...sentSnap.docs, ...receivedSnap.docs].map((d) => this.toMessage(d.id, d.data()));
  }

  async listByGroup(groupId: string): Promise<Message[]> {
    const snap = await this.messages.where('groupId', '==', groupId).get();
    return snap.docs
      .map((d) => this.toMessage(d.id, d.data()))
      .sort((x, y) => x.sendTime.localeCompare(y.sendTime));
  }

  async markSeen(messageId: string): Promise<void> {
    await this.messages.doc(messageId).update({ isSeen: true });
  }

  // ==== Nhom chat ====

  async createGroup(group: Omit<ChatGroup, 'groupId'>): Promise<ChatGroup> {
    const ref = await this.groups.add(group);
    return { groupId: ref.id, ...group };
  }

  async findGroup(groupId: string): Promise<ChatGroup | null> {
    const snap = await this.groups.doc(groupId).get();
    if (!snap.exists) {
      return null;
    }
    const data = snap.data() ?? {};
    return {
      groupId: snap.id,
      groupName: data.groupName ?? '',
      memberIds: data.memberIds ?? [],
      createdBy: data.createdBy ?? '',
      createdAt: data.createdAt ?? '',
    };
  }

  async listGroupsByMember(uid: string): Promise<ChatGroup[]> {
    const snap = await this.groups.where('memberIds', 'array-contains', uid).get();
    return snap.docs.map((d) => {
      const data = d.data();
      return {
        groupId: d.id,
        groupName: data.groupName ?? '',
        memberIds: data.memberIds ?? [],
        createdBy: data.createdBy ?? '',
        createdAt: data.createdAt ?? '',
      };
    });
  }

  private toMessage(messageId: string, data: FirebaseFirestore.DocumentData): Message {
    return {
      messageId,
      senderId: data.senderId ?? '',
      receiverId: data.receiverId ?? data.recipientId,
      groupId: data.groupId,
      messageType: data.messageType ?? 'TEXT',
      content: data.content ?? '',
      sendTime: data.sendTime ?? data.createdAt ?? '',
      isSeen: data.isSeen ?? data.isRead ?? false,
    };
  }
}
