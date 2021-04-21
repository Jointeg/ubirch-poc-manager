import { PocStatus } from '../enums/poc-status.enum';

export interface Poc {
    id: number;
    pocId: number;
    name: string;
    deviceId: string;
    folderIdentifier: string;
    createdAt: Date;
    updatedAt?: Date;
    status: PocStatus;
}
