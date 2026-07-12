import { FirebaseService } from '../firebase/firebase.service';
import { AdminRepository } from './admin.repository';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let firebase: { auth: jest.Mock };
  let adminRepo: jest.Mocked<AdminRepository>;

  const authUsers = [
    {
      uid: 'u1',
      email: 'an@test.com',
      displayName: 'An',
      disabled: false,
      metadata: { creationTime: '2026-01-01' },
    },
    {
      uid: 'u2',
      email: 'binh@test.com',
      displayName: 'Binh',
      disabled: true,
      metadata: { creationTime: '2026-01-02' },
    },
  ];

  beforeEach(() => {
    firebase = {
      auth: jest.fn().mockReturnValue({
        listUsers: jest.fn().mockResolvedValue({ users: authUsers }),
        updateUser: jest.fn().mockResolvedValue({}),
        setCustomUserClaims: jest.fn().mockResolvedValue(undefined),
      }),
    };
    adminRepo = {
      getStats: jest.fn(),
      getFullNames: jest.fn().mockResolvedValue(new Map([['u1', 'Nguyen Van An']])),
    } as unknown as jest.Mocked<AdminRepository>;

    service = new AdminService(firebase as unknown as FirebaseService, adminRepo);
  });

  it('listUsers: tim theo email + enrich fullName tu Firestore', async () => {
    const result = await service.listUsers({ search: 'an@', page: 1, limit: 20 });

    expect(result.total).toBe(1);
    expect(result.items[0].uid).toBe('u1');
    expect(result.items[0].fullName).toBe('Nguyen Van An'); // enrich tu Firestore
  });

  it('listUsers: khong search thi tra het + phan trang', async () => {
    const result = await service.listUsers({ page: 1, limit: 1 });
    expect(result.total).toBe(2);
    expect(result.items).toHaveLength(1);
  });

  it('setUserDisabled: goi updateUser voi disabled', async () => {
    const result = await service.setUserDisabled('u2', false);
    expect(firebase.auth().updateUser).toHaveBeenCalledWith('u2', { disabled: false });
    expect(result).toEqual({ uid: 'u2', disabled: false });
  });
});
