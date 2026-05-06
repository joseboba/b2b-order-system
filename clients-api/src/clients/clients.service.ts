import { Injectable, NotFoundException } from '@nestjs/common';
import { Client } from './client.interface';
import { Segment, TaxRegime } from './client.enums';

@Injectable()
export class ClientsService {
  private readonly clients: Map<string, Client> = new Map([
    ['CLI-99821', { clientId: 'CLI-99821', name: 'Distribuidora Andina S.A.S', segment: Segment.Wholesale, taxRegime: TaxRegime.VatRegistered, region: 'Valle del Cauca' }],
    ['CLI-10034', { clientId: 'CLI-10034', name: 'Supermercados del Norte S.A.S', segment: Segment.Wholesale, taxRegime: TaxRegime.VatRegistered, region: 'Atlántico' }],
    ['CLI-20567', { clientId: 'CLI-20567', name: 'Tienda Don Pedro', segment: Segment.Retail, taxRegime: TaxRegime.VatExempt, region: 'Cundinamarca' }],
    ['CLI-30891', { clientId: 'CLI-30891', name: 'Almacenes Medellín Ltda', segment: Segment.Wholesale, taxRegime: TaxRegime.VatRegistered, region: 'Antioquia' }],
    ['CLI-40124', { clientId: 'CLI-40124', name: 'Distribuciones Costa S.A.S', segment: Segment.Wholesale, taxRegime: TaxRegime.VatRegistered, region: 'Bolívar' }],
    ['CLI-50678', { clientId: 'CLI-50678', name: 'Minimarket La Esquina', segment: Segment.Retail, taxRegime: TaxRegime.VatExempt, region: 'Valle del Cauca' }],
  ]);

  findById(clientId: string): Client {
    const client = this.clients.get(clientId);
    if (!client) throw new NotFoundException({ error: 'client not found', clientId });
    return client;
  }
}
